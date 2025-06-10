package stasis.client_android.activities.fragments.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.DialogAvailableCommandsBinding
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.name
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.parametersAsString
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigRepository.Companion.savedLastProcessedCommand
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import stasis.core.commands.proto.Command
import java.time.Instant

class AvailableCommandsDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogAvailableCommandsBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            arguments.retrieveCommands {
                when (it) {
                    is Try.Success -> {
                        val preferences: SharedPreferences = ConfigRepository.getPreferences(requireContext())
                        val lastProcessedCommand = preferences.savedLastProcessedCommand()

                        if (it.value.isNotEmpty()) {
                            binding.commandsInProgress.isVisible = false
                            binding.commandsError.isVisible = false
                            binding.commandsListEmpty.isVisible = false
                            binding.commandsList.isVisible = true

                            binding.commandsList.adapter =
                                CommandsListItemAdapter(
                                    context = requireContext(),
                                    resource = R.layout.list_item_command,
                                    commands = it.value.sortedBy { command -> command.sequenceId }.reversed(),
                                    lastProcessedCommand = lastProcessedCommand
                                )
                        } else {
                            binding.commandsInProgress.isVisible = false
                            binding.commandsError.isVisible = false
                            binding.commandsListEmpty.isVisible = true
                            binding.commandsList.isVisible = false

                            binding.commandsListEmpty.text = getString(
                                if (lastProcessedCommand == 0L) {
                                    R.string.settings_commands_empty_without_processed
                                } else {
                                    R.string.settings_commands_empty_with_processed
                                }
                            ).renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = lastProcessedCommand.toString(),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                        }
                    }

                    is Try.Failure -> {
                        binding.commandsInProgress.isVisible = false
                        binding.commandsError.isVisible = true
                        binding.commandsListEmpty.isVisible = false
                        binding.commandsList.isVisible = false

                        binding.commandsError.text = getString(R.string.settings_commands_error)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = it.exception.message ?: it.exception.javaClass.simpleName,
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                    }
                }
            }
        }

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
        data class Arguments(
            val retrieveCommands: (f: (Try<List<Command>>) -> Unit) -> Unit,
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.AvailableCommandsDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.AvailableCommandsDialogFragment"
    }

    class CommandsListItemAdapter(
        context: Context,
        private val resource: Int,
        private val commands: List<Command>,
        private val lastProcessedCommand: Long
    ) : ArrayAdapter<Command>(context, resource, commands) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val command = commands[position]

            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

            val commandContainer: LinearLayout = layout.findViewById(R.id.command_container)
            val commandInfo: TextView = layout.findViewById(R.id.command_info)
            val commandDetails: TextView = layout.findViewById(R.id.command_details)

            commandInfo.text = context.getString(
                if (command.sequenceId > lastProcessedCommand) R.string.command_field_content_info_unprocessed
                else R.string.command_field_content_info_processed
            )
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = command.sequenceId.toString(),
                        style = StyleSpan(Typeface.NORMAL)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = command.name(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%3\$s",
                        content = Instant.ofEpochMilli(command.created).formatAsFullDateTime(context),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            commandDetails.text = context.getString(R.string.command_field_content_details)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = context.getString(
                            when (command.source) {
                                "user" -> R.string.command_field_content_source_user
                                "service" -> R.string.command_field_content_source_service
                                else -> R.string.command_field_content_source_unknown
                            }
                        ),
                        style = StyleSpan(Typeface.ITALIC)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = context.getString(
                            if (command.target != null) R.string.command_field_content_details_target_current_device
                            else R.string.command_field_content_details_target_any_device
                        ),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            commandContainer.setOnClickListener {
                InformationDialogFragment()
                    .withIcon(R.drawable.ic_debug)
                    .withTitle(
                        context.getString(
                            R.string.settings_command_parameters_title,
                            command.sequenceId.toString(),
                            command.name()
                        )
                    )
                    .withMessage(
                        context.getString(
                            R.string.settings_command_parameters_info,
                            command.parametersAsString()
                        )
                    )
                    .show(FragmentManager.findFragmentManager(layout))
            }

            return layout
        }
    }
}
