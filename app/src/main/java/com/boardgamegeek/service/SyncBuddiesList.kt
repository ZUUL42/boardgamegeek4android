package com.boardgamegeek.service

import android.accounts.Account
import android.content.SyncResult
import android.support.annotation.StringRes
import com.boardgamegeek.BggApplication
import com.boardgamegeek.R
import com.boardgamegeek.auth.AccountUtils
import com.boardgamegeek.auth.Authenticator
import com.boardgamegeek.io.BggService
import com.boardgamegeek.model.Buddy
import com.boardgamegeek.model.User
import com.boardgamegeek.model.persister.BuddyPersister
import com.boardgamegeek.pref.SyncPrefs
import com.boardgamegeek.provider.BggContract
import com.boardgamegeek.provider.BggContract.Buddies
import com.boardgamegeek.util.DateTimeUtils
import com.boardgamegeek.util.PreferencesUtils
import com.boardgamegeek.util.PresentationUtils
import com.boardgamegeek.util.RemoteConfig
import timber.log.Timber
import java.io.IOException

/**
 * Syncs the list of buddies. Only runs every few days.
 */
class SyncBuddiesList(application: BggApplication, service: BggService, syncResult: SyncResult, private val account: Account) : SyncTask(application, service, syncResult) {
    @StringRes
    private var currentDetailResId: Int = 0
    private var persister = BuddyPersister(this.application)

    override val syncType = SyncService.FLAG_SYNC_BUDDIES

    override val notificationSummaryMessageId = R.string.sync_notification_buddies_list

    private val fetchIntervalInDays = RemoteConfig.getInt(RemoteConfig.KEY_SYNC_BUDDIES_FETCH_INTERVAL_DAYS)

    override fun execute() {
        Timber.i("Syncing list of buddies...")
        try {
            if (!PreferencesUtils.getSyncBuddies(context)) {
                Timber.i("...buddies not set to sync")
                return
            }

            val lastCompleteSync = SyncPrefs.getBuddiesTimestamp(context)
            if (lastCompleteSync >= 0 && DateTimeUtils.howManyDaysOld(lastCompleteSync) < fetchIntervalInDays) {
                Timber.i("...skipping; we synced already within the last $fetchIntervalInDays days")
                return
            }

            persister.resetTimestamp()

            updateNotification(R.string.sync_notification_buddies_list_downloading)
            val user = requestUser() ?: return

            updateNotification(R.string.sync_notification_buddies_list_storing)
            storeUserInAuthenticator(user)
            persistUser(user)

            updateNotification(R.string.sync_notification_buddies_list_pruning)
            pruneOldBuddies()

            SyncPrefs.setBuddiesTimestamp(context, persister.timestamp)
        } finally {
            Timber.i("...complete!")
        }
    }

    private fun updateNotification(@StringRes detailResId: Int) {
        currentDetailResId = detailResId
        updateProgressNotification(context.getString(detailResId))
    }

    private fun requestUser(): User? {
        var user: User? = null
        val call = service.user(account.name, 1, 1)
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                showError(context.getString(currentDetailResId), response.code())
                syncResult.stats.numIoExceptions++
                cancel()
            }
            user = response.body()
        } catch (e: IOException) {
            showError(context.getString(currentDetailResId), e)
            syncResult.stats.numIoExceptions++
            cancel()
        }

        return user
    }

    private fun storeUserInAuthenticator(user: User) {
        Authenticator.putUserId(context, user.id)
        AccountUtils.setUsername(context, user.name)
        AccountUtils.setFullName(context, PresentationUtils.buildFullName(user.firstName, user.lastName))
        AccountUtils.setAvatarUrl(context, user.avatarUrl)
    }

    private fun persistUser(user: User) {
        var count = persister.saveBuddy(user.id, user.name, false)

        val buddies = user.buddies?.buddies ?: listOf<Buddy>()
        for (buddy in buddies) {
            val userId = buddy.id.toIntOrNull() ?: BggContract.INVALID_ID
            count += persister.saveBuddy(userId, buddy.name, true)
        }

        syncResult.stats.numEntries += count.toLong()
        Timber.i("Synced %,d buddies", count)
    }

    private fun pruneOldBuddies() {
        val resolver = context.contentResolver
        val count = resolver.delete(Buddies.CONTENT_URI,
                Buddies.UPDATED_LIST + "<?",
                arrayOf(persister.timestamp.toString()))
        syncResult.stats.numDeletes += count.toLong()
        Timber.i("Pruned %,d users who are no longer buddies", count)
    }
}
