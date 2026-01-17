package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.*

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }
        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
        
        if (flag == Constants.FLAG_LAUNCH_APP) {
            showKeyboardWithFocus()
        }
    }

    private fun initViews() {
        binding.search.queryHint = when (flag) {
            Constants.FLAG_HIDDEN_APPS -> getString(R.string.hidden_apps)
            in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP -> "Please select an app"
            else -> binding.search.queryHint
        }
        
        try {
            val searchTextView = binding.search.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
            searchTextView?.gravity = prefs.appLabelAlignment
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim() ?: ""
                when {
                    q.startsWith("!") -> requireContext().openUrl(Constants.URL_DUCK_SEARCH + q.replace(" ", "%20"))
                    adapter.itemCount == 0 -> requireContext().openSearch(q)
                    else -> adapter.launchFirstInList()
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                adapter.filter.filter(newText)
                binding.appDrawerTip.visibility = View.GONE
                binding.appRename.visibility = if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                return true
            }
        })
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { app ->
                if (app.appPackage.isEmpty()) return@AppDrawerAdapter
                viewModel.selectedApp(app, flag)
                val dest = if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS) R.id.mainFragment else null
                if (dest != null) findNavController().popBackStack(dest, false) else findNavController().popBackStack()
            },
            appInfoListener = { app ->
                openAppInfo(requireContext(), app.user, app.appPackage)
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { app ->
                if (requireContext().isSystemApp(app.appPackage)) {
                    requireContext().showToast(R.string.system_app_cannot_delete)
                } else {
                    requireContext().uninstall(app.appPackage)
                }
            },
            appHideListener = { appModel, _ ->
                val newSet = prefs.hiddenApps.toMutableSet()
                val entry = "${appModel.appPackage}|${appModel.user}"
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // Legacy support
                    newSet.remove(entry)
                } else {
                    newSet.add(entry)
                }

                prefs.hiddenApps = newSet
                
                if (flag == Constants.FLAG_HIDDEN_APPS && newSet.isEmpty()) {
                    findNavController().popBackStack()
                }
                
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }

                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                viewModel.getAppList()
            }
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                if (dx - scrollRange < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    checkMessageAndExit()
                }
                return scrollRange
            }
        }

        binding.recyclerView.apply {
            layoutManager = linearLayoutManager
            this.adapter = this@AppDrawerFragment.adapter
            addOnScrollListener(getRecyclerViewOnScrollListener())
            itemAnimator = null
            if (!requireContext().isEinkDisplay()) {
                layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
            }
        }
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) { isFirst ->
            if (isFirst && flag == Constants.FLAG_LAUNCH_APP) {
                binding.appDrawerTip.visibility = View.VISIBLE
                binding.appDrawerTip.isSelected = true
            }
        }
        
        val liveData = if (flag == Constants.FLAG_HIDDEN_APPS) viewModel.hiddenApps else viewModel.appList
        liveData.observe(viewLifecycleOwner) { list ->
            list?.let {
                adapter.setAppList(it.toMutableList())
                if (flag != Constants.FLAG_HIDDEN_APPS) adapter.filter.filter(binding.search.query)
            }
        }
    }

    private fun initClickListeners() {
        binding.appDrawerTip.setOnClickListener {
            it.isSelected = false
            it.isSelected = true
        }
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(R.string.type_a_new_app_name_first)
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
                prefs.setHomeAppName(flag, name)
            }
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener() = object : RecyclerView.OnScrollListener() {
        var onTop = false

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    onTop = !recyclerView.canScrollVertically(-1)
                    if (onTop) binding.search.hideKeyboard()
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    if (!recyclerView.canScrollVertically(1)) {
                        binding.search.hideKeyboard()
                    } else if (!recyclerView.canScrollVertically(-1)) {
                        if (!onTop && !isRemoving) showKeyboardWithFocus()
                    }
                }
            }
        }
    }

    private fun checkMessageAndExit() {
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP) viewModel.checkForMessages()
    }

    private fun showKeyboardWithFocus() {
        if (prefs.autoShowKeyboard) {
            binding.search.post {
                val searchEditText = binding.search.findViewById<View>(androidx.appcompat.R.id.search_src_text)
                searchEditText?.requestFocus()
                binding.search.postDelayed({
                    if (isAdded && !isRemoving) {
                        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(searchEditText ?: binding.search, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, 200)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (flag == Constants.FLAG_LAUNCH_APP) {
            showKeyboardWithFocus()
        }
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
