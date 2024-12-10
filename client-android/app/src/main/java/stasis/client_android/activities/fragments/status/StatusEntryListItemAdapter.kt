package stasis.client_android.activities.fragments.status

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import stasis.client_android.R

class StatusEntryListItemAdapter(
    context: Context,
    private val resource: Int,
    private val fragments: List<Fragment>
) : ArrayAdapter<Fragment>(context, resource, fragments) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))
        val fragmentManager = FragmentManager.findFragmentManager(layout)
        val fragment = fragments[position]

        val infoContainer = layout.findViewById<LinearLayout>(R.id.status_entry_info)
        val title = layout.findViewById<TextView>(R.id.status_entry_title)
        val description = layout.findViewById<TextView>(R.id.status_entry_description)
        val icon = layout.findViewById<ImageView>(R.id.status_entry_operation)
        val details = layout.findViewById<FragmentContainerView>(R.id.status_entry_details)

        when (position) {
            0 -> {
                title.text = context.getString(R.string.user_header_title)
                description.text = context.getString(R.string.user_header_description)
            }

            1 -> {
                title.text = context.getString(R.string.device_header_title)
                description.text = context.getString(R.string.device_header_description)
            }

            2 -> {
                title.text = context.getString(R.string.server_header_title)
                description.text = context.getString(R.string.server_header_description)
            }
        }

        infoContainer.setOnClickListener {
            if (details.isVisible) {
                fragmentManager
                    .beginTransaction()
                    .remove(fragment)
                    .commit()

                icon.setImageResource(R.drawable.ic_status_expand)
            } else {
                fragmentManager
                    .beginTransaction()
                    .add(details, fragment, fragment.javaClass.canonicalName)
                    .commit()

                icon.setImageResource(R.drawable.ic_status_collapse)
            }

            details.isVisible = !details.isVisible
        }

        return layout
    }

    companion object {
        operator fun invoke(context: Context) = StatusEntryListItemAdapter(
            context = context,
            resource = R.layout.list_item_status_entry,
            fragments = mutableListOf(
                UserDetailsFragment(),
                DeviceDetailsFragment(),
                ConnectionsFragment()
            )
        )
    }
}
