package com.boardgamegeek.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.os.Handler
import android.support.annotation.PluralsRes
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Pair
import android.view.*
import com.boardgamegeek.R
import com.boardgamegeek.auth.Authenticator
import com.boardgamegeek.entities.Status
import com.boardgamegeek.extensions.*
import com.boardgamegeek.ui.adapter.Callback
import com.boardgamegeek.ui.adapter.SearchResultsAdapter
import com.boardgamegeek.ui.viewmodel.SearchViewModel
import com.boardgamegeek.ui.widget.SafeViewTarget
import com.boardgamegeek.util.HelpUtils
import com.boardgamegeek.util.PreferencesUtils
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
import com.crashlytics.android.answers.SearchEvent
import com.github.amlcurran.showcaseview.ShowcaseView
import kotlinx.android.synthetic.main.fragment_search_results.*
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast
import java.util.*

class SearchResultsFragment : Fragment(), ActionMode.Callback {

    private var showcaseView: ShowcaseView? = null
    private var actionMode: ActionMode? = null

    private val snackbar: Snackbar by lazy {
        Snackbar.make(containerView, "", Snackbar.LENGTH_INDEFINITE).apply {
            view.setBackgroundResource(R.color.dark_blue)
            setActionTextColor(ContextCompat.getColor(ctx, R.color.accent))
        }
    }

    private val viewModel: SearchViewModel by lazy {
        ViewModelProviders.of(act).get(SearchViewModel::class.java)
    }

    private val searchResultsAdapter: SearchResultsAdapter by lazy {
        SearchResultsAdapter(
                object : Callback {
                    override fun onItemClick(position: Int): Boolean {
                        if (actionMode == null) return false
                        toggleSelection(position)
                        return true
                    }

                    override fun onItemLongClick(position: Int): Boolean {
                        if (actionMode != null) return false
                        actionMode = act.startActionMode(this@SearchResultsFragment)
                        if (actionMode == null) return false
                        toggleSelection(position)
                        return true
                    }

                    private fun toggleSelection(position: Int) {
                        searchResultsAdapter.toggleSelection(position)
                        val count = searchResultsAdapter.selectedItemCount
                        if (count == 0) {
                            actionMode?.finish()
                        } else {
                            actionMode?.title = resources.getQuantityString(R.plurals.msg_games_selected, count, count)
                            actionMode?.invalidate()
                        }
                    }
                })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        viewModel.searchResults.observe(this, Observer { resource ->
            if (resource == null) return@Observer

            when (resource.status) {
                Status.REFRESHING -> progressView.fadeIn()
                Status.ERROR -> {
                    if (resource.message.isBlank()) {
                        emptyView.setText(R.string.empty_http_error) // TODO better message?
                    } else {
                        emptyView.text = getString(R.string.empty_http_error, resource.message)
                    }
                    emptyView.fadeIn()
                    recyclerView.fadeOut()
                    progressView.fadeOut()
                }
                Status.SUCCESS -> {
                    val data = resource.data
                    val query = viewModel.query.value
                    if (data == null || data.isEmpty()) {
                        if (query != null && query.second)
                            viewModel.searchInexact(query.first)
                        if (query == null || query.first.isBlank()) {
                            emptyView.setText(R.string.search_initial_help)
                        } else {
                            emptyView.setText(R.string.empty_search)
                        }
                        searchResultsAdapter.clear()
                        emptyView.fadeIn()
                        recyclerView.fadeOut()
                    } else {
                        searchResultsAdapter.results = data
                        emptyView.fadeOut()
                        recyclerView.fadeIn(isResumed)
                    }
                    if (query != null) {
                        showSnackbar(query.first, query.second, data?.size ?: 0)
                    }
                    progressView.fadeOut()
                }
            }

            maybeShowHelp()
        })
    }

    private fun showSnackbar(queryText: String, isExactMatch: Boolean, count: Int) {
        if (queryText.isBlank()) {
            snackbar.dismiss()
        } else {
            @PluralsRes val messageId = if (isExactMatch) R.plurals.search_results_exact else R.plurals.search_results
            snackbar.setText(resources.getQuantityString(messageId, count, count, queryText))
            if (isExactMatch) {
                snackbar.setAction(R.string.more) {
                    search(queryText, false)
                    Answers.getInstance().logCustom(CustomEvent("SearchMore"))
                }
            } else {
                snackbar.setAction("", null)
            }
            snackbar.show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.layoutManager = LinearLayoutManager(ctx)
        recyclerView.addItemDecoration(DividerItemDecoration(ctx, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = searchResultsAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.help, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.menu_help) {
            showHelp()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun maybeShowHelp() {
        if (HelpUtils.shouldShowHelp(ctx, HelpUtils.HELP_SEARCHRESULTS_KEY, HELP_VERSION)) {
            Handler().postDelayed({ showHelp() }, 100)
        }
    }

    private fun showHelp() {
        val builder = HelpUtils.getShowcaseBuilder(act)
        if (builder != null) {
            builder.setContentText(R.string.help_searchresults)
                    .setOnClickListener {
                        showcaseView?.hide()
                        HelpUtils.updateHelp(ctx, HelpUtils.HELP_SEARCHRESULTS_KEY, HELP_VERSION)
                    }
            val viewTarget = SafeViewTarget(HelpUtils.getRecyclerViewVisibleChild(recyclerView))
            builder.setTarget(viewTarget)
            showcaseView = builder.build()?.apply {
                this.setButtonPosition(HelpUtils.getCenterLeftLayoutParams(ctx))
                this.show()
            }
        }
    }

    fun search(query: String) {
        search(query, true)
    }

    private fun search(query: String, shouldSearchExact: Boolean) {
        if (!isAdded) return
        progressView.fadeIn()
        Answers.getInstance().logSearch(SearchEvent().putQuery(query))
        if (shouldSearchExact) {
            viewModel.search(query)
        } else {
            viewModel.searchInexact(query)
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.game_context, menu)
        searchResultsAdapter.clearSelections()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = searchResultsAdapter.selectedItemCount
        menu.findItem(R.id.menu_log_play).isVisible = Authenticator.isSignedIn(ctx) && count == 1 && PreferencesUtils.showLogPlay(ctx)
        menu.findItem(R.id.menu_log_play_quick).isVisible = Authenticator.isSignedIn(ctx) && PreferencesUtils.showQuickLogPlay(ctx)
        menu.findItem(R.id.menu_link).isVisible = count == 1
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        searchResultsAdapter.clearSelections()
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (searchResultsAdapter.getSelectedItems().isEmpty()) {
            return false
        }
        val game = searchResultsAdapter.getItem(searchResultsAdapter.getSelectedItems()[0])
        when (item.itemId) {
            R.id.menu_log_play -> {
                game?.let {
                    LogPlayActivity.logPlay(ctx, it.id, it.name, null, null, null, false)
                }
                mode.finish()
                return true
            }
            R.id.menu_log_play_quick -> {
                toast(resources.getQuantityString(R.plurals.msg_logging_plays, searchResultsAdapter.selectedItemCount))
                for (position in searchResultsAdapter.getSelectedItems()) {
                    searchResultsAdapter.getItem(position)?.let {
                        ctx.logQuickPlay(it.id, it.name)
                    }
                }
                mode.finish()
                return true
            }
            R.id.menu_share -> {
                val shareMethod = "Search"
                if (searchResultsAdapter.selectedItemCount == 1) {
                    game?.let { act.shareGame(it.id, it.name, shareMethod) }
                } else {
                    val games = ArrayList<Pair<Int, String>>(searchResultsAdapter.selectedItemCount)
                    for (position in searchResultsAdapter.getSelectedItems()) {
                        searchResultsAdapter.getItem(position)?.let {
                            games.add(Pair.create(it.id, it.name))
                        }
                    }
                    act.shareGames(games, shareMethod)
                }
                mode.finish()
                return true
            }
            R.id.menu_link -> {
                game?.let { ctx.linkBgg(it.id) }
                mode.finish()
                return true
            }
        }
        return false
    }

    companion object {
        private const val HELP_VERSION = 2

        fun newInstance(): SearchResultsFragment {
            return SearchResultsFragment()
        }
    }
}
