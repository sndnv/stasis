package stasis.client_android.activities.fragments.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.fragments.settings.PermissionsDialogFragment
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.databinding.FragmentSourcesBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.sources.calendar.CalendarSource
import stasis.client_android.sources.contacts.ContactsSource
import stasis.client_android.utils.Permissions.LibraryPermission
import stasis.client_android.utils.Permissions.getRequiredPermissionsStatus
import stasis.client_android.utils.Permissions.hasPermission
import javax.inject.Inject

@AndroidEntryPoint
class SourcesFragment : Fragment() {
    @Inject
    lateinit var rules: RuleViewModel

    private lateinit var binding: FragmentSourcesBinding
    private var currentRules: List<Rule> = emptyList()

    private val requestCalendarPermissions: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            onLibraryPermissionResult(CalendarSource.Scheme, result)
        }

    private val requestContactsPermissions: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            onLibraryPermissionResult(ContactsSource.Scheme, result)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_sources, container, false)

        binding.sourcesPermissionsOverview.setOnClickListener { showPermissionsDialog() }
        binding.sourcesFilesContainer.setOnClickListener { showPermissionsDialog() }

        rules.rules.observe(viewLifecycleOwner) { updated ->
            currentRules = updated
            render()
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        renderFiles()
        renderLibrary(
            binding.sourcesCalendarSwitch,
            binding.sourcesCalendarStatus,
            binding.sourcesCalendarSets,
            LibraryPermission.Calendar,
            CalendarSource.Scheme,
            requestCalendarPermissions
        )
        renderLibrary(
            binding.sourcesContactsSwitch,
            binding.sourcesContactsStatus,
            binding.sourcesContactsSets,
            LibraryPermission.Contacts,
            ContactsSource.Scheme,
            requestContactsPermissions
        )
    }

    private fun renderFiles() {
        val granted = requireActivity().getRequiredPermissionsStatus().all { it.second }

        binding.sourcesFilesStatus.text = getString(
            if (granted) R.string.sources_files_status_granted else R.string.sources_files_status_missing
        )

        renderStatusIcon(binding.sourcesFilesStatusIcon, granted)
    }

    private fun renderLibrary(
        switch: SwitchMaterial,
        status: TextView,
        counter: TextView,
        permission: LibraryPermission,
        scheme: String,
        request: ActivityResultLauncher<Array<String>>
    ) {
        val context = requireContext()
        val granted = context.hasPermission(permission.read) && context.hasPermission(permission.write)

        status.text = getString(
            if (granted) R.string.sources_library_status_granted else R.string.sources_library_status_missing
        )

        val enabledCount = SourceRules.enabledSets(currentRules, scheme).size
        if (enabledCount == 0) {
            counter.visibility = View.GONE
        } else {
            val inDefault = SourceRules.isInDefault(currentRules, scheme)
            val totalSets = SourceRules.allSets(currentRules).size

            counter.visibility = View.VISIBLE
            counter.text = resources.getQuantityString(
                if (inDefault) R.plurals.sources_sets_summary else R.plurals.sources_sets_summary_warning,
                totalSets,
                enabledCount,
                totalSets
            )
            counter.setTextColor(
                context.getColor(if (inDefault) R.color.secondary_light else R.color.design_default_color_error)
            )
        }

        switch.setOnCheckedChangeListener(null)
        switch.isChecked = SourceRules.isEnabled(currentRules, scheme)
        switch.setOnCheckedChangeListener { _, checked ->
            if (checked) enableSource(scheme, permission, request) else disableSource(scheme)
        }
    }

    private fun renderStatusIcon(icon: ImageView, granted: Boolean) {
        if (granted) {
            icon.setImageResource(R.drawable.ic_check)
            icon.setColorFilter(requireContext().getColor(R.color.launcher_tertiary_2))
        } else {
            icon.setImageResource(R.drawable.ic_close)
            icon.setColorFilter(requireContext().getColor(R.color.design_default_color_error))
        }
    }

    private fun enableSource(
        scheme: String,
        permission: LibraryPermission,
        request: ActivityResultLauncher<Array<String>>
    ) {
        val context = requireContext()
        if (context.hasPermission(permission.read) && context.hasPermission(permission.write)) {
            applyEnable(scheme)
        } else {
            request.launch(arrayOf(permission.read, permission.write))
        }
    }

    private fun applyEnable(scheme: String) {
        val targets = SourceRules.setsMissing(currentRules, scheme)

        when {
            targets.isEmpty() -> render()
            targets.size > 1 -> {
                render()

                ConfirmationDialogFragment()
                    .withTitle(getString(R.string.sources_add_confirm_title))
                    .withMessage(
                        resources.getQuantityString(
                            R.plurals.sources_add_confirm_content,
                            targets.size,
                            schemeLabel(scheme),
                            targets.size
                        )
                    )
                    .withConfirmationHandler { addToSets(scheme, targets) }
                    .show(childFragmentManager)
            }

            else -> addToSets(scheme, targets)
        }
    }

    private fun addToSets(scheme: String, targets: Set<DatasetDefinitionId?>) {
        targets.forEach { definition ->
            rules.put(
                Rule(
                    id = 0,
                    operation = Rule.Operation.Include,
                    source = "$scheme:/",
                    pattern = "*",
                    definition = definition
                )
            )
        }
    }

    private fun disableSource(scheme: String) {
        render()

        val enabledSets = SourceRules.enabledSets(currentRules, scheme).size

        ConfirmationDialogFragment()
            .withTitle(getString(R.string.sources_remove_confirm_title))
            .withMessage(
                resources.getQuantityString(
                    R.plurals.sources_remove_confirm_content,
                    enabledSets,
                    schemeLabel(scheme),
                    enabledSets
                )
            )
            .withConfirmationHandler {
                SourceRules.matching(currentRules, scheme).forEach { rules.delete(it.id) }
            }
            .show(childFragmentManager)
    }

    private fun onLibraryPermissionResult(scheme: String, result: Map<String, Boolean>) {
        if (result.values.all { it }) {
            applyEnable(scheme)
        } else {
            render()
        }
    }

    private fun schemeLabel(scheme: String): String =
        when (scheme) {
            CalendarSource.Scheme -> getString(R.string.sources_calendar_title)
            ContactsSource.Scheme -> getString(R.string.sources_contacts_title)
            else -> scheme
        }

    private fun showPermissionsDialog() {
        PermissionsDialogFragment().show(childFragmentManager, PermissionsDialogFragment.DialogTag)
    }
}
