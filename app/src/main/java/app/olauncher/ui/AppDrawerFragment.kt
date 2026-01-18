package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import androidx.appcompat.R as AppCompatR
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogEditCategoriesBinding
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.*

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var currentCategory = "Alle"
    private var fullAppList: List<AppModel> = emptyList()

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
        
        arguments?.getString("start_category")?.let {
            currentCategory = it
        }

        initViews()
        initSearch()
        initCategories()
        initAdapter()
        initObservers()
        initClickListeners()
        
        if (flag == Constants.FLAG_LAUNCH_APP) {
            showKeyboardWithFocus()
        } else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
            binding.search.clearFocus()
        }
    }

    private fun initViews() {
        binding.search.queryHint = when (flag) {
            Constants.FLAG_HIDDEN_APPS -> getString(R.string.hidden_apps)
            in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP -> "Please select an app"
            else -> binding.search.queryHint
        }
        
        try {
            // Accessing the internal TextView of SearchView using AppCompatR to avoid unresolved reference issues.
            val searchTextView = binding.search.findViewById<TextView>(AppCompatR.id.search_src_text)
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

    private fun initCategories() {
        categoryAdapter = CategoryAdapter(Constants.CATEGORIES, currentCategory) { category ->
            if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
                // In den Prefs speichern (damit der Homescreen weiß, dass es eine Kategorie ist)
                prefs.setHomeCategory(flag, category)
                viewModel.selectedCategory(category, flag)
                findNavController().popBackStack()
            } else {
                currentCategory = category
                filterAppsByCategory()
            }
        }
        
        binding.categoryRecyclerView.adapter = categoryAdapter
        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        
        val isHomeSelection = flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8
        binding.categoryRecyclerView.visibility = if (flag == Constants.FLAG_LAUNCH_APP || isHomeSelection) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun filterAppsByCategory() {
        val filtered = when (currentCategory) {
            "Alle" -> fullAppList
            "Unkategorisiert" -> fullAppList.filter { app ->
                prefs.getAppCategories(app.appPackage).isEmpty()
            }
            else -> fullAppList.filter { app ->
                prefs.getAppCategories(app.appPackage).contains(currentCategory)
            }
        }
        
        // 1. Animation sofort entfernen
        binding.recyclerView.layoutAnimation = null 
        
        // 2. Daten im Adapter aktualisieren
        adapter.setAppList(filtered.toMutableList())
        
        // 3. Den Filter-Vorgang starten
        adapter.filter.filter(binding.search.query) {
            // Dieser Callback kommt, wenn der Filter fertig ist.
            // Wir nutzen .post {}, um sicherzugehen, dass die RecyclerView 
            // die neuen Items bereits intern verarbeitet hat.
            binding.recyclerView.post {
                linearLayoutManager.scrollToPositionWithOffset(0, 0)
                
                // 4. Animation erst nach dem Scrollen (optional) wieder aktivieren
                if (isAdded && !requireContext().isEinkDisplay()) {
                    binding.recyclerView.postDelayed({
                        if (isAdded) {
                            binding.recyclerView.layoutAnimation = AnimationUtils.loadLayoutAnimation(
                                requireContext(), R.anim.layout_anim_from_bottom
                            )
                        }
                    }, 100) // Kurze Verzögerung, damit das Scrollen Vorrang hat
                }
            }
        }

        if (currentCategory == "Alle" || currentCategory == "Unkategorisiert") {
            showKeyboardWithFocus()
        } else {
            binding.search.hideKeyboard()
        }
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { app ->
                if (app.appPackage.isEmpty()) return@AppDrawerAdapter
                
                // Wenn eine App gewählt wird, muss die Kategorie für diesen Slot gelöscht werden
                if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
                    prefs.setHomeCategory(flag, null)
                }

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
            },
            appCategoryListener = { appModel ->
                showEditCategoriesDialog(appModel)
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

    private fun showEditCategoriesDialog(appModel: AppModel) {
        val dialogBinding = DialogEditCategoriesBinding.inflate(layoutInflater)
        val editCategories = Constants.CATEGORIES.filter { it != "Alle" && it != "Unkategorisiert" }
        val currentCats = prefs.getAppCategories(appModel.appPackage)
        val editAdapter = EditCategoryAdapter(editCategories, currentCats)
        
        dialogBinding.categoriesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.categoriesRecyclerView.adapter = editAdapter
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.AppTheme)
            .setView(dialogBinding.root)
            .create()
            
        dialogBinding.saveCategories.setOnClickListener {
            prefs.setAppCategories(appModel.appPackage, editAdapter.getSelectedCategories())
            filterAppsByCategory()
            dialog.dismiss()
        }
        
        dialog.show()
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
                fullAppList = it
                filterAppsByCategory()
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
        if (isRemoving) return
        binding.search.hideKeyboard()
        findNavController().popBackStack()
    }

    private fun showKeyboardWithFocus() {
        if (isAdded && prefs.autoShowKeyboard) {
            binding.search.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.search.findViewById(AppCompatR.id.search_src_text), InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
