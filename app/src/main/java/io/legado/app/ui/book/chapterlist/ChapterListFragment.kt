package io.legado.app.ui.book.chapterlist

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.legado.app.App
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.BookHelp
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.getViewModelOfActivity
import io.legado.app.utils.observeEvent
import kotlinx.android.synthetic.main.fragment_chapter_list.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.sdk27.listeners.onClick

class ChapterListFragment : VMBaseFragment<ChapterListViewModel>(R.layout.fragment_chapter_list),
    ChapterListAdapter.Callback,
    ChapterListViewModel.ChapterListCallBack{
    override val viewModel: ChapterListViewModel
        get() = getViewModelOfActivity(ChapterListViewModel::class.java)

    lateinit var adapter: ChapterListAdapter
    private var book: Book? = null
    private var durChapterIndex = 0
    private lateinit var mLayoutManager: UpLinearLayoutManager
    private var tocLiveData: LiveData<List<BookChapter>>? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.chapterCallBack = this
        initRecyclerView()
        initView()
        initBook()
    }

    private fun initRecyclerView() {
        adapter = ChapterListAdapter(requireContext(), this)
        mLayoutManager = UpLinearLayoutManager(requireContext())
        recycler_view.layoutManager = mLayoutManager
        recycler_view.addItemDecoration(VerticalDivider(requireContext()))
        recycler_view.adapter = adapter
    }

    private fun initView() {
        ll_chapter_base_info.setBackgroundColor(backgroundColor)
        iv_chapter_top.onClick { mLayoutManager.scrollToPositionWithOffset(0, 0) }
        iv_chapter_bottom.onClick {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        tv_current_chapter_info.onClick {
            mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
        }
    }

    private fun initBook() {
        launch {
            withContext(IO) {
                book = App.db.bookDao().getBook(viewModel.bookUrl)
            }
            initDoc()
            book?.let {
                durChapterIndex = it.durChapterIndex
                tv_current_chapter_info.text = it.durChapterTitle
                mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
                initCacheFileNames(it)
            }
        }
    }

    private fun initDoc() {
        tocLiveData?.removeObservers(this@ChapterListFragment)
        tocLiveData = App.db.bookChapterDao().observeByBook(viewModel.bookUrl)
        tocLiveData?.observe(viewLifecycleOwner, Observer {
            adapter.setItems(it)
            mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
        })
    }

    private fun initCacheFileNames(book: Book) {
        launch(IO) {
            adapter.cacheFileNames.addAll(BookHelp.getChapterFiles(book))
            withContext(Main) {
                adapter.notifyItemRangeChanged(0, adapter.getActualItemCount(), true)
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<BookChapter>(EventBus.SAVE_CONTENT) { chapter ->
            book?.bookUrl?.let { bookUrl ->
                if (chapter.bookUrl == bookUrl) {
                    adapter.cacheFileNames.add(BookHelp.formatChapterName(chapter))
                    adapter.notifyItemChanged(chapter.index, true)
                }
            }
        }
    }

    override fun startChapterListSearch(newText: String?) {
        if (newText.isNullOrBlank()) {
            initDoc()
        } else {
            tocLiveData?.removeObservers(this)
            tocLiveData = App.db.bookChapterDao().liveDataSearch(viewModel.bookUrl, newText)
            tocLiveData?.observe(viewLifecycleOwner, Observer {
                adapter.setItems(it)
            })
        }
    }

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }

    override fun openChapter(bookChapter: BookChapter) {
        activity?.setResult(RESULT_OK, Intent().putExtra("index", bookChapter.index))
        activity?.finish()
    }

}