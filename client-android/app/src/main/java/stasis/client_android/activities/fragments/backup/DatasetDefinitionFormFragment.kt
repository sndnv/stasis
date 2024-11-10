package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.asChronoUnit
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.fromPolicyTypeString
import stasis.client_android.activities.helpers.Common.getOrRenderFailure
import stasis.client_android.activities.helpers.Common.toFields
import stasis.client_android.activities.helpers.Common.toPolicyTypeString
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentDatasetDefinitionFormBinding
import stasis.client_android.databinding.InputDatasetDefinitionRetentionBinding
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@AndroidEntryPoint
class DatasetDefinitionFormFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentDatasetDefinitionFormBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dataset_definition_form,
            container,
            false
        )

        val controller = findNavController()

        val args: DatasetDefinitionFormFragmentArgs by navArgs()

        fun actionHandler(fields: Fields, definition: DatasetDefinition?) {
            if (fields.validate()) {
                when (definition) {
                    null -> datasets.createDefinition(request = fields.toCreateRequest(forDevice = datasets.self)) {
                        it.getOrRenderFailure(withContext = requireContext())
                            ?.let {
                                Toast.makeText(
                                    binding.root.context,
                                    getString(R.string.toast_dataset_definition_created, fields.info),
                                    Toast.LENGTH_SHORT
                                ).show()

                                controller.popBackStack()
                            }
                    }

                    else -> datasets.updateDefinition(
                        definition = definition.id,
                        request = fields.toUpdateRequest()
                    ) {
                        it.getOrRenderFailure(withContext = requireContext())
                            ?.let {
                                Toast.makeText(
                                    binding.root.context,
                                    getString(R.string.toast_dataset_definition_updated, fields.info),
                                    Toast.LENGTH_SHORT
                                ).show()

                                controller.popBackStack()
                            }
                    }

                }
            }
        }

        when (val definitionId = args.definition) {
            null -> {
                binding.definitionFormAction.text = getString(R.string.dataset_definition_create)
                val fields = initFields(binding = binding, definition = null)
                binding.definitionFormAction.setOnClickListener { actionHandler(fields, null) }
            }

            else -> {
                binding.definitionLoadInProgress.isVisible = true
                binding.definitionFormContainer.isVisible = false
                datasets.definition(definitionId).observeOnce(viewLifecycleOwner) { definition ->
                    binding.definitionLoadInProgress.isVisible = false
                    binding.definitionFormContainer.isVisible = true
                    binding.definitionFormAction.text = getString(R.string.dataset_definition_update)
                    val fields = initFields(binding = binding, definition = definition)
                    binding.definitionFormAction.setOnClickListener { actionHandler(fields, definition) }
                }
            }
        }

        return binding.root
    }

    private fun initFields(
        binding: FragmentDatasetDefinitionFormBinding,
        definition: DatasetDefinition?
    ): Fields {
        val context = binding.root.context

        binding.info.editText?.setText(definition?.info)
        binding.info.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dataset_definition_field_title_info)
                .setMessage(getString(R.string.dataset_definition_field_help_info))
                .show()
        }

        initRetention(
            binding = binding.existingVersions,
            retention = definition?.existingVersions ?: Defaults.ExistingVersions,
            helpTitleId = R.string.dataset_definition_field_title_existing_versions,
            helpMessageId = R.string.dataset_definition_field_help_existing_versions
        )

        initRetention(
            binding = binding.removedVersions,
            retention = definition?.removedVersions ?: Defaults.RemovedVersions,
            helpTitleId = R.string.dataset_definition_field_title_removed_versions,
            helpMessageId = R.string.dataset_definition_field_help_removed_versions
        )

        return Fields(
            infoField = binding.info,
            existingVersionsDurationTypeField = binding.existingVersions.retentionDuration.durationType,
            existingVersionsDurationAmountField = binding.existingVersions.retentionDuration.durationAmount,
            existingVersionsRetentionPolicyTypeField = binding.existingVersions.retentionPolicyType,
            existingVersionsRetentionPolicyVersionsField = binding.existingVersions.retentionPolicyVersions,
            removedVersionsDurationTypeField = binding.removedVersions.retentionDuration.durationType,
            removedVersionsDurationAmountField = binding.removedVersions.retentionDuration.durationAmount,
            removedVersionsRetentionPolicyTypeField = binding.removedVersions.retentionPolicyType,
            removedVersionsRetentionPolicyVersionsField = binding.removedVersions.retentionPolicyVersions,
            context = context
        )
    }

    private fun initRetention(
        binding: InputDatasetDefinitionRetentionBinding,
        retention: DatasetDefinition.Retention,
        @StringRes helpTitleId: Int,
        @StringRes helpMessageId: Int
    ) {
        val context = binding.root.context

        val durationTypesAdapter = ArrayAdapter(
            context,
            R.layout.dropdown_duration_type_item,
            Defaults.DurationTypes.map { it.asString(context) }
        )

        val retentionDuration = retention.duration.toFields()

        binding.retentionDuration.durationAmountValue = retentionDuration.first.toString()
        val retentionDurationTypeView =
            binding.retentionDuration.durationType.editText as? AutoCompleteTextView
        retentionDurationTypeView?.setAdapter(durationTypesAdapter)
        retentionDurationTypeView?.setText(
            retentionDuration.second.asString(context),
            false
        )

        binding.retentionDuration.durationAmount.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(helpTitleId)
                .setMessage(getString(helpMessageId))
                .show()
        }

        val policyTypesAdapter = ArrayAdapter(
            context,
            R.layout.dropdown_policy_type_item,
            Defaults.PolicyTypes.map { it.toPolicyTypeString(context) }
        )

        val policyVersionsView = binding.retentionPolicyVersions as? TextInputLayout
        policyVersionsView?.isVisible = false

        val policyTypeView = binding.retentionPolicyType.editText as? AutoCompleteTextView
        policyTypeView?.setAdapter(policyTypesAdapter)
        policyTypeView?.setText(retention.policy.toPolicyTypeString(context), false)
        policyTypeView?.setOnItemClickListener { _, _, position, _ ->
            policyVersionsView?.isVisible = position == 0
        }
    }

    object Defaults {
        val RedundantCopies: Int = 2

        val ExistingVersions: DatasetDefinition.Retention = DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = Duration.of(7, ChronoUnit.DAYS)
        )

        val RemovedVersions: DatasetDefinition.Retention = DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.LatestOnly,
            duration = Duration.of(21, ChronoUnit.DAYS)
        )

        val DurationTypes: List<ChronoUnit> = listOf(
            ChronoUnit.SECONDS,
            ChronoUnit.MINUTES,
            ChronoUnit.HOURS,
            ChronoUnit.DAYS
        )

        val PolicyTypes: List<String> = listOf(
            "at-most",
            "latest-only",
            "all"
        )
    }

    class Fields(
        private val infoField: TextInputLayout,
        private val existingVersionsDurationTypeField: TextInputLayout,
        private val existingVersionsDurationAmountField: TextInputLayout,
        private val existingVersionsRetentionPolicyTypeField: TextInputLayout,
        private val existingVersionsRetentionPolicyVersionsField: TextInputLayout,
        private val removedVersionsDurationTypeField: TextInputLayout,
        private val removedVersionsDurationAmountField: TextInputLayout,
        private val removedVersionsRetentionPolicyTypeField: TextInputLayout,
        private val removedVersionsRetentionPolicyVersionsField: TextInputLayout,
        private val context: Context
    ) {

        fun validate(): Boolean {
            val infoValid = infoField.validateText {
                context.getString(R.string.dataset_definition_field_error_info)
            }

            val existingVersionsDurationAmountValid =
                existingVersionsDurationAmountField.validateDurationAmount {
                    context.getString(R.string.dataset_definition_field_error_existing_versions_duration)
                }

            val removedVersionsDurationAmountValid =
                removedVersionsDurationAmountField.validateDurationAmount {
                    context.getString(R.string.dataset_definition_field_error_removed_versions_duration)
                }

            existingVersionsDurationTypeField.isErrorEnabled = !existingVersionsDurationAmountValid
            existingVersionsDurationTypeField.error =
                if (existingVersionsDurationAmountValid) null
                else context.getString(R.string.dataset_definition_field_error_existing_versions_duration_padding)

            removedVersionsDurationTypeField.isErrorEnabled = !removedVersionsDurationAmountValid
            removedVersionsDurationTypeField.error =
                if (removedVersionsDurationAmountValid) null
                else context.getString(R.string.dataset_definition_field_error_removed_versions_duration_padding)

            val existingVersionsRetentionPolicyVersionValid =
                when (existingVersionsRetentionPolicyTypeField.editText?.text.toString()
                    .fromPolicyTypeString(context)) {
                    "at-most" -> existingVersionsRetentionPolicyVersionsField.validateInt {
                        context.getString(R.string.dataset_definition_field_error_existing_versions_policy_versions)
                    }

                    else -> true
                }

            val removedVersionsRetentionPolicyVersionValid =
                when (removedVersionsRetentionPolicyTypeField.editText?.text.toString()
                    .fromPolicyTypeString(context)) {
                    "at-most" -> removedVersionsRetentionPolicyVersionsField.validateInt {
                        context.getString(R.string.dataset_definition_field_error_removed_versions_policy_versions)
                    }

                    else -> true
                }

            val existingVersionsValid =
                existingVersionsDurationAmountValid && existingVersionsRetentionPolicyVersionValid
            val removedVersionsValid =
                removedVersionsDurationAmountValid && removedVersionsRetentionPolicyVersionValid

            return infoValid && existingVersionsValid && removedVersionsValid
        }

        fun toCreateRequest(forDevice: DeviceId): CreateDatasetDefinition = CreateDatasetDefinition(
            info = info,
            device = forDevice,
            redundantCopies = Defaults.RedundantCopies,
            existingVersions = existingVersions,
            removedVersions = removedVersions
        )

        fun toUpdateRequest(): UpdateDatasetDefinition = UpdateDatasetDefinition(
            info = info,
            redundantCopies = Defaults.RedundantCopies,
            existingVersions = existingVersions,
            removedVersions = removedVersions
        )

        val info: String
            get() = infoField.editText?.text.toString().trim()

        val existingVersions: DatasetDefinition.Retention
            get() {
                val durationType = existingVersionsDurationTypeField.editText?.text.toString()
                    .asChronoUnit(context)
                val durationAmount =
                    existingVersionsDurationAmountField.editText?.text.toString().toLong()

                val policy = extractPolicy(
                    policyTypeField = existingVersionsRetentionPolicyTypeField,
                    policyVersionsField = existingVersionsRetentionPolicyVersionsField
                )

                return DatasetDefinition.Retention(
                    policy = policy,
                    duration = Duration.of(durationAmount, durationType)
                )
            }

        val removedVersions: DatasetDefinition.Retention
            get() {
                val durationType =
                    removedVersionsDurationTypeField.editText?.text.toString().asChronoUnit(context)
                val durationAmount =
                    removedVersionsDurationAmountField.editText?.text.toString().toLong()

                val policy = extractPolicy(
                    policyTypeField = removedVersionsRetentionPolicyTypeField,
                    policyVersionsField = removedVersionsRetentionPolicyVersionsField
                )

                return DatasetDefinition.Retention(
                    policy = policy,
                    duration = Duration.of(durationAmount, durationType)
                )
            }

        fun TextInputLayout.validateText(message: () -> String): Boolean =
            validate(isValid = { it?.isNotEmpty() ?: true }, message = message)

        fun TextInputLayout.validateInt(message: () -> String): Boolean =
            validate(
                isValid = {
                    it?.let {
                        try {
                            it.toInt() > 0
                        } catch (e: NumberFormatException) {
                            false
                        }
                    } ?: true
                },
                message = message
            )

        fun TextInputLayout.validateDurationAmount(message: () -> String): Boolean =
            validateInt(message)

        private fun TextInputLayout.validate(
            isValid: (content: String?) -> Boolean,
            message: () -> String
        ): Boolean {
            val isInvalid = !isValid(editText?.text?.toString())

            isErrorEnabled = isInvalid
            error = if (isInvalid) message() else null

            return !isInvalid
        }

        private fun extractPolicy(
            policyTypeField: TextInputLayout,
            policyVersionsField: TextInputLayout
        ): DatasetDefinition.Retention.Policy {
            return when (val actualPolicyType =
                policyTypeField.editText?.text.toString().fromPolicyTypeString(context)) {
                "at-most" -> DatasetDefinition.Retention.Policy.AtMost(
                    versions = policyVersionsField.editText?.text.toString().toInt()
                )

                "latest-only" -> DatasetDefinition.Retention.Policy.LatestOnly
                "all" -> DatasetDefinition.Retention.Policy.All
                else -> throw IllegalArgumentException("Unexpected policy type encountered: [$actualPolicyType]")
            }
        }
    }
}
