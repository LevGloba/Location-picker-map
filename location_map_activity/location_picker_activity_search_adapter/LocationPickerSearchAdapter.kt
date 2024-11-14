package location_map_activity.location_picker_activity_search_adapter

import android.location.Address
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.capstime.timecapsule.R
import timber.log.Timber

class MapSearchAdapter(private val dataSet: List<MapSearchObject>) :
    RecyclerView.Adapter<MapSearchAdapter.ViewHolder>() {

    init {
        Timber.tag("mapActivity").i("MapSearchAdapter init")
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val textView: TextView

        init {
            textView = view.findViewById(R.id.text_view_recycler_map_activity)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        Timber.tag("mapActivity").i("onCreateViewHolder")
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_search_map_activity, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        val mapSearchObject = dataSet[position]
        Timber.tag("mapActivity").i("onBindViewHolder : $mapSearchObject")
        viewHolder.textView.text = mapSearchObject.description
        viewHolder.itemView.setOnClickListener {

            mapSearchObject.onCallback(mapSearchObject.address)
            mapSearchObject.clearAutofocusesViewAndHideKeyboard()

        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size.also {
        Timber.tag("mapActivity").i("getItemCount : $it")
    }
}

data class MapSearchObject(
    val description: String,
    val address: Address,
    val onCallback: (Address) -> Unit,
    val clearAutofocusesViewAndHideKeyboard: () -> Unit
)
