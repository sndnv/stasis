package stasis.client_android.activities.fragments.search

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsLocalTime
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentSearchBinding
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getDateTimeFormat
import java.time.Instant
import java.time.LocalTime
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    private lateinit var binding: FragmentSearchBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_search, container, false)

        initUntilInput(
            existingUntilInstant = savedInstanceState?.getString(SelectedUntilInstantKey)?.let { Instant.parse(it) }
        )

        val searchResultAdapter = SearchResultListItemAdapter()
        binding.searchResult.adapter = searchResultAdapter

        binding.searchQueryTextInput.doOnTextChanged { _, _, _, _ ->
            binding.searchControls.visibility = View.VISIBLE
            binding.runSearch.visibility = View.VISIBLE
        }

        binding.searchQueryTextInput.setOnClickListener {
            binding.searchControls.visibility = View.VISIBLE
            binding.runSearch.visibility = View.VISIBLE
        }

        binding.runSearch.setOnClickListener {
            providerContext.analytics.recordEvent(name = "search_dataset_metadata")

            searchResultAdapter.setResult(result = null)

            val isInvalid = binding.searchQuery.editText?.text.toString().isBlank()

            binding.searchQuery.isErrorEnabled = isInvalid
            binding.searchQuery.error =
                if (isInvalid) context.getString(R.string.search_field_error_query_empty) else null

            if (!isInvalid) {
                val query = binding.searchQuery.editText?.text.toString()

                val regex = if (PlainChars.matcher(query).matches()) {
                    ".*$query.*".toRegex(RegexOption.IGNORE_CASE)
                } else {
                    Try {
                        query.toRegex(RegexOption.IGNORE_CASE)
                    }.getOrElse {
                        Regex.fromLiteral(query)
                    }
                }

                val until = (binding.searchUntil.date.text to binding.searchUntil.time.text)
                    .parseAsDateTime(context)

                binding.searchResultInProgress.isVisible = true
                binding.searchResultEmpty.isVisible = false
                binding.searchResult.isVisible = false

                datasets.search(regex, until).observe(viewLifecycleOwner) { result ->
                    searchResultAdapter.setResult(result)

                    if (result.definitions.none { it.value != null }) {
                        binding.searchResultInProgress.isVisible = false
                        binding.searchResultEmpty.isVisible = true
                        binding.searchResult.isVisible = false

                        binding.searchResultEmpty.text = getString(R.string.search_result_empty)
                            .renderAsSpannable(
                                Common.StyledString(
                                    placeholder = "%1\$s",
                                    content = query,
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                    } else {
                        binding.searchResultInProgress.isVisible = false
                        binding.searchResultEmpty.isVisible = false
                        binding.searchResult.isVisible = true

                        binding.searchResultEmpty.text = null
                    }
                }

                binding.searchControls.visibility = View.GONE
                binding.runSearch.visibility = View.GONE
            }
        }

        return binding.root
    }

    private fun initUntilInput(existingUntilInstant: Instant?) {
        val context = requireContext()
        val preferences = ConfigRepository.getPreferences(context)

        val untilInstant = existingUntilInstant ?: Instant.now()

        binding.searchUntil.date.text = untilInstant.formatAsDate(context)
        binding.searchUntil.time.text = untilInstant.formatAsTime(context)

        binding.searchUntil.date.setOnClickListener {
            val selected = binding.searchUntil.date.text.parseAsDate(context)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selected.toEpochMilli())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                binding.searchUntil.date.text = Instant.ofEpochMilli(selection).formatAsDate(context)
            }

            datePicker.show(parentFragmentManager, datePicker.toString())
        }

        binding.searchUntil.time.setOnClickListener {
            val selected = binding.searchUntil.time.text.parseAsLocalTime(context)

            val timePickerBuilder = MaterialTimePicker.Builder()
                .setHour(selected.hour)
                .setMinute(selected.minute)

            if (preferences.getDateTimeFormat() == Settings.DateTimeFormat.Iso) {
                timePickerBuilder.setTimeFormat(TimeFormat.CLOCK_24H)
            }

            val timePicker = timePickerBuilder.build()

            timePicker.addOnPositiveButtonClickListener {
                binding.searchUntil.time.text =
                    LocalTime.of(timePicker.hour, timePicker.minute).formatAsTime(context)
            }

            timePicker.show(parentFragmentManager, timePicker.toString())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            SelectedUntilInstantKey,
            (binding.searchUntil.date.text to binding.searchUntil.time.text)
                .parseAsDateTime(requireContext())
                .toString()
        )
        super.onSaveInstanceState(outState)
    }

    companion object {
        private val PlainChars: Pattern = Pattern.compile("[\\w _-]*", Pattern.CASE_INSENSITIVE)

        private const val SelectedUntilInstantKey: String =
            "stasis.client_android.activities.fragments.search.SearchFragment.state.until"
    }
}
