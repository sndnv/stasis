package stasis.client_android.activities.fragments.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.DialogPermissionsBinding
import stasis.client_android.utils.Permissions.getLibraryPermissionsStatus
import stasis.client_android.utils.Permissions.getRequiredPermissionsStatus
import stasis.client_android.utils.Permissions.needsExtraPermissions
import stasis.client_android.utils.Permissions.requestMissingPermissions

class PermissionsDialogFragment : DialogFragment() {
    private lateinit var binding: DialogPermissionsBinding

    private var optionalExpanded: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogPermissionsBinding.inflate(inflater)
        return binding.root
    }

    override fun onResume() {
        val activity = requireActivity()

        val permissionsMissing = activity.needsExtraPermissions()

        renderPermissions()

        if (permissionsMissing) {
            binding.permissionsRequestButton.text =
                getString(R.string.permissions_request_button_request_action_text)
            binding.permissionsRequestButton.contentDescription =
                getString(R.string.permissions_request_button_request_action_hint)
            binding.permissionsRequestButton.tooltipText =
                getString(R.string.permissions_request_button_request_action_hint)
            binding.permissionsRequestButton.setIconResource(R.drawable.ic_add_permissions)
            binding.permissionsRequestButton.setOnClickListener {
                activity.requestMissingPermissions()
            }
        } else {
            binding.permissionsRequestButton.text =
                getString(R.string.permissions_request_button_close_action_text)
            binding.permissionsRequestButton.contentDescription =
                getString(R.string.permissions_request_button_close_action_hint)
            binding.permissionsRequestButton.tooltipText =
                getString(R.string.permissions_request_button_close_action_hint)
            binding.permissionsRequestButton.setIconResource(0)
            binding.permissionsRequestButton.setOnClickListener {
                dismiss()
            }
        }

        binding.permissionsNotShown.isVisible = permissionsMissing
        binding.permissionsNotShown.setOnClickListener {
            PermissionsNotShownInformationDialogFragment()
                .withTitle(getString(R.string.permissions_dialog_not_shown_hint))
                .withMessage(getString(R.string.permissions_dialog_not_shown_info))
                .show(childFragmentManager)
        }

        super.onResume()
    }

    private fun renderPermissions() {
        val required = requireActivity().getRequiredPermissionsStatus().sortedBy { it.first }
        val optional = requireContext().getLibraryPermissionsStatus().sortedBy { it.first }

        val rows = buildList {
            add(PermissionRow.Section(R.string.permissions_section_essential, collapsible = false, expanded = true))
            addAll(required.map { PermissionRow.Entry(name = it.first, granted = it.second) })
            add(
                PermissionRow.Section(
                    R.string.permissions_section_optional,
                    collapsible = true,
                    expanded = optionalExpanded
                )
            )
            if (optionalExpanded) {
                addAll(optional.map { PermissionRow.Entry(name = it.first, granted = it.second) })
            }
        }

        binding.permissionsList.adapter = PermissionsListItemAdapter(
            context = requireContext(),
            permissions = rows,
            onToggleOptional = {
                optionalExpanded = !optionalExpanded
                renderPermissions()
            }
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.PermissionsDialogFragment"
    }

    class PermissionsNotShownInformationDialogFragment : InformationDialogFragment() {
        override fun init(): MaterialAlertDialogBuilder {
            return super.init()
                .setPositiveButton(R.string.permissions_dialog_not_shown_button_hint) { dialog, _ ->
                    dialog.dismiss()
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:stasis.client.android".toUri()
                        )
                    )
                }

        }
    }

    sealed interface PermissionRow {
        data class Section(@param:StringRes val title: Int, val collapsible: Boolean, val expanded: Boolean) :
            PermissionRow

        data class Entry(val name: String, val granted: Boolean) : PermissionRow
    }

    class PermissionsListItemAdapter(
        context: Context,
        private val permissions: List<PermissionRow>,
        private val onToggleOptional: () -> Unit,
    ) : ArrayAdapter<PermissionRow>(context, R.layout.list_item_permission, permissions) {
        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int =
            if (permissions[position] is PermissionRow.Section) SectionType else EntryType

        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean =
            when (val row = permissions[position]) {
                is PermissionRow.Entry -> true
                is PermissionRow.Section -> row.collapsible
            }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val row = permissions[position]) {
                is PermissionRow.Section -> sectionView(row, convertView, parent)
                is PermissionRow.Entry -> entryView(row, convertView, parent)
            }

        private fun sectionView(row: PermissionRow.Section, convertView: View?, parent: ViewGroup): View {
            val layout = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_permission_section, parent, false)

            val title: TextView = layout.findViewById(R.id.permission_section_title)
            val chevron: ImageView = layout.findViewById(R.id.permission_section_chevron)

            title.text = context.getString(row.title)

            if (row.collapsible) {
                chevron.visibility = View.VISIBLE
                chevron.setImageResource(
                    if (row.expanded) R.drawable.ic_tree_arrow_down else R.drawable.ic_tree_arrow_right
                )
                layout.setOnClickListener { onToggleOptional() }
            } else {
                chevron.visibility = View.GONE
                layout.setOnClickListener(null)
                layout.isClickable = false
            }

            return layout
        }

        private fun entryView(row: PermissionRow.Entry, convertView: View?, parent: ViewGroup): View {
            val (name, granted) = row

            val layout = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_permission, parent, false)

            val permissionInfo: TextView = layout.findViewById(R.id.permission_info)
            val permissionDetails: TextView = layout.findViewById(R.id.permission_details)
            val permissionGranted: ImageView = layout.findViewById(R.id.permission_granted)

            val permissionNameStringId: Int? = when (name) {
                Manifest.permission.READ_MEDIA_AUDIO -> R.string.permissions_read_media_audio_name
                Manifest.permission.READ_MEDIA_IMAGES -> R.string.permissions_read_media_images_name
                Manifest.permission.READ_MEDIA_VIDEO -> R.string.permissions_read_media_video_name
                Manifest.permission.POST_NOTIFICATIONS -> R.string.permissions_post_notifications_name
                Manifest.permission.READ_EXTERNAL_STORAGE -> R.string.permissions_read_external_storage_name
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> R.string.permissions_write_external_storage_name
                Manifest.permission.MANAGE_EXTERNAL_STORAGE -> R.string.permissions_manage_external_storage_name
                Manifest.permission.READ_CALENDAR -> R.string.permissions_read_calendar_name
                Manifest.permission.WRITE_CALENDAR -> R.string.permissions_write_calendar_name
                Manifest.permission.READ_CONTACTS -> R.string.permissions_read_contacts_name
                Manifest.permission.WRITE_CONTACTS -> R.string.permissions_write_contacts_name
                else -> null
            }

            val permissionHintStringId: Int = when (name) {
                Manifest.permission.READ_MEDIA_AUDIO -> R.string.permissions_read_media_audio_hint
                Manifest.permission.READ_MEDIA_IMAGES -> R.string.permissions_read_media_images_hint
                Manifest.permission.READ_MEDIA_VIDEO -> R.string.permissions_read_media_video_hint
                Manifest.permission.POST_NOTIFICATIONS -> R.string.permissions_post_notifications_hint
                Manifest.permission.READ_EXTERNAL_STORAGE -> R.string.permissions_read_external_storage_hint
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> R.string.permissions_write_external_storage_hint
                Manifest.permission.MANAGE_EXTERNAL_STORAGE -> R.string.permissions_manage_external_storage_hint
                Manifest.permission.READ_CALENDAR -> R.string.permissions_read_calendar_hint
                Manifest.permission.WRITE_CALENDAR -> R.string.permissions_write_calendar_hint
                Manifest.permission.READ_CONTACTS -> R.string.permissions_read_contacts_hint
                Manifest.permission.WRITE_CONTACTS -> R.string.permissions_write_contacts_hint
                else -> R.string.permissions_unrecognized_hint
            }

            permissionInfo.text = context.getString(R.string.permissions_field_content_info)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = permissionNameStringId?.let { context.getString(it) }
                            ?: name.split(".").last(),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            permissionDetails.maxLines = 3

            permissionDetails.text = context.getString(R.string.permissions_field_content_details)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = context.getString(permissionHintStringId),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            if (granted) {
                permissionGranted.setImageResource(R.drawable.ic_check)
                permissionGranted.setColorFilter(context.getColor(R.color.launcher_tertiary_2))
            } else {
                permissionGranted.setImageResource(R.drawable.ic_close)
                permissionGranted.setColorFilter(context.getColor(R.color.design_default_color_error))
            }


            return layout
        }

        companion object {
            private const val SectionType: Int = 0
            private const val EntryType: Int = 1
        }
    }
}
