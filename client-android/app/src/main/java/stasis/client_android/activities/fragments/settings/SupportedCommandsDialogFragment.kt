package stasis.client_android.activities.fragments.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.databinding.DialogSupportedCommandsBinding

class SupportedCommandsDialogFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogSupportedCommandsBinding.inflate(inflater)

        binding.commandsList.adapter =
            CommandsListItemAdapter(
                context = requireContext(),
                resource = R.layout.list_item_command,
                commands = listOf("logout_user", "unrecognized"),
            )

        return binding.root
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
            "stasis.client_android.activities.fragments.settings.SupportedCommandsDialogFragment"
    }

    class CommandsListItemAdapter(
        context: Context,
        private val resource: Int,
        private val commands: List<String>,
    ) : ArrayAdapter<String>(context, resource, commands) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val command = commands[position]

            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

            val commandInfo: TextView = layout.findViewById(R.id.command_info)
            val commandDetails: TextView = layout.findViewById(R.id.command_details)

            commandInfo.text = context.getString(R.string.command_field_content_supported_command_name)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = (position + 1).toString(),
                        style = StyleSpan(Typeface.NORMAL)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = command,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            commandDetails.maxLines = 3

            commandDetails.text = context.getString(R.string.command_field_content_supported_command_details)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = context.getString(
                            when (command) {
                                "logout_user" -> R.string.command_field_content_supported_command_details_logout_user
                                else -> R.string.command_field_content_supported_command_details_unrecognized
                            }
                        ),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            return layout
        }
    }
}
