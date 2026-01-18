package app.olauncher.ui

import android.content.Context
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appCategoryListener: (AppModel) -> Unit
) : ListAdapter<AppModel, AppDrawerAdapter.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel) =
                oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel) = oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appModel = getItem(position)
        if (appModel.appPackage.isEmpty() && appModel.appLabel.isEmpty()) {
            holder.bindEmpty()
            return
        }
        holder.bind(
            flag,
            appLabelGravity,
            myUserHandle,
            appModel,
            appClickListener,
            appDeleteListener,
            appInfoListener,
            appHideListener,
            appRenameListener,
            appCategoryListener
        )
    }

    override fun getFilter(): Filter = appFilter

    private fun createAppFilter() = object : Filter() {
        override fun performFiltering(charSearch: CharSequence?): FilterResults {
            isBangSearch = charSearch?.startsWith("!") ?: false
            autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

            val filtered = if (charSearch.isNullOrBlank()) {
                appsList
            } else {
                appsList.filter { appLabelMatches(it.appLabel, charSearch) }
            }
            
            return FilterResults().apply { values = filtered }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            appFilteredList = (results?.values as? List<AppModel>)?.toMutableList() ?: mutableListOf()
            submitList(appFilteredList) {
                if (itemCount == 1 && autoLaunch && !isBangSearch && flag == Constants.FLAG_LAUNCH_APP && appFilteredList.isNotEmpty()) {
                    appClickListener(appFilteredList[0])
                }
            }
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: CharSequence): Boolean {
        val normalizedLabel = Normalizer.normalize(appLabel, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[-_+,. ]"), "")
        return appLabel.contains(charSearch.trim(), true) || normalizedLabel.contains(charSearch.trim(), true)
    }

    fun setAppList(newList: MutableList<AppModel>) {
        // Add empty app for bottom padding
        val list = newList.toMutableList().apply {
            add(AppModel("", null, "", "", false, android.os.Process.myUserHandle()))
        }
        appsList = list
        appFilteredList = list
        submitList(list)
    }

    fun launchFirstInList() {
        if (appFilteredList.isNotEmpty()) appClickListener(appFilteredList[0])
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bindEmpty() = with(binding) {
            val params = root.layoutParams
            params.height = root.context.resources.getDimensionPixelSize(R.dimen.app_drawer_bottom_padding)
            root.layoutParams = params
            
            appTitle.visibility = View.GONE
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            otherProfileIndicator.visibility = View.GONE
        }

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appCategoryListener: (AppModel) -> Unit
        ) = with(binding) {
            val params = root.layoutParams
            if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                root.layoutParams = params
            }

            appHideLayout.isVisible = false
            renameLayout.isVisible = false
            appTitle.isVisible = true
            
            appTitle.text = if (appModel.isNew) {
                root.context.getString(R.string.app_label_with_star, appModel.appLabel)
            } else {
                appModel.appLabel
            }
            
            appTitle.gravity = appLabelGravity
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener { clickListener(appModel) }
            appTitle.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha = if (root.context.isSystemApp(appModel.appPackage)) 0.5f else 1.0f
                    appHide.text = root.context.getString(if (flag == Constants.FLAG_HIDDEN_APPS) R.string.adapter_show else R.string.adapter_hide)
                    appTitle.isVisible = false
                    appHideLayout.isVisible = true
                    appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                }
                true
            }

            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getOriginalAppName(it.context, appModel.appPackage)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.isVisible = true
                    appHideLayout.isVisible = false
                    etAppRename.showKeyboard()
                }
            }

            etAppRename.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveRename(appModel, appRenameListener)
                    true
                } else false
            }

            tvSaveRename.setOnClickListener {
                saveRename(appModel, appRenameListener)
            }

            listOf(appMenuClose, appRenameClose).forEach {
                it.setOnClickListener {
                    appHideLayout.isVisible = false
                    renameLayout.isVisible = false
                    appTitle.isVisible = true
                    etAppRename.hideKeyboard()
                }
            }

            appInfo.setOnClickListener { appInfoListener(appModel) }
            appDelete.setOnClickListener { appDeleteListener(appModel) }
            appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
            
            appCategories.setOnClickListener {
                appCategoryListener(appModel)
            }
        }

        private fun saveRename(appModel: AppModel, listener: (AppModel, String) -> Unit) {
            val newName = binding.etAppRename.text.toString().trim()
            if (newName.isNotBlank()) {
                listener(appModel, newName)
            } else {
                listener(appModel, getOriginalAppName(binding.root.context, appModel.appPackage))
            }
            binding.renameLayout.isVisible = false
            binding.appTitle.isVisible = true
            binding.etAppRename.hideKeyboard()
        }

        private fun getOriginalAppName(context: Context, packageName: String): String = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) { "" }
    }
}
