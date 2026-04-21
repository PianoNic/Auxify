package org.oxycblt.auxio.spotify

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.sync.Subscription
import org.oxycblt.auxio.sync.SubscriptionManager
import org.oxycblt.auxio.sync.SyncDatabaseProvider

class SpotifyLibraryFragment : Fragment() {

    private lateinit var subscriptionManager: SubscriptionManager
    private val items = mutableListOf<SpotifyItem>()
    private lateinit var adapter: SpotifyItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_spotify_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        subscriptionManager = SubscriptionManager(requireContext())

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.library_list)
        val loading = view.findViewById<ProgressBar>(R.id.loading)
        val emptyText = view.findViewById<TextView>(R.id.empty_text)
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)

        adapter = SpotifyItemAdapter(items) { item, isSubscribed ->
            lifecycleScope.launch {
                if (isSubscribed) {
                    subscriptionManager.subscribe(item.id, item.type, item.name, item.imageUrl)
                } else {
                    subscriptionManager.unsubscribe(item.id)
                }
            }
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        tabLayout.addTab(tabLayout.newTab().setText("Playlists"))
        tabLayout.addTab(tabLayout.newTab().setText("Albums"))
        tabLayout.addTab(tabLayout.newTab().setText("Liked Songs"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                loadTab(tab.position, loading, emptyText)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        if (!SpotifySession.isLoggedIn) {
            emptyText.text = "Login to Spotify first"
            emptyText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            loadTab(0, loading, emptyText)
        }
    }

    private fun loadTab(position: Int, loading: ProgressBar, emptyText: TextView) {
        loading.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        items.clear()
        adapter.notifyDataSetChanged()

        lifecycleScope.launch {
            val subscriptions = subscriptionManager.getSubscriptions()
            val subscribedIds = subscriptions.map { it.spotifyId }.toSet()

            // TODO: Fetch from KotifyClient API once session is wired
            // For now show placeholder data demonstrating the UI
            val newItems = when (position) {
                0 -> listOf(
                    SpotifyItem("liked_songs", "liked_songs", "Liked Songs", "All your liked songs", "", subscribedIds.contains("liked_songs"))
                )
                else -> emptyList()
            }

            withContext(Dispatchers.Main) {
                items.clear()
                items.addAll(newItems)
                adapter.notifyDataSetChanged()
                loading.visibility = View.GONE
                if (items.isEmpty()) {
                    emptyText.text = "Nothing found"
                    emptyText.visibility = View.VISIBLE
                }
            }
        }
    }
}

data class SpotifyItem(
    val id: String,
    val type: String,
    val name: String,
    val subtitle: String,
    val imageUrl: String,
    var isSubscribed: Boolean = false
)

class SpotifyItemAdapter(
    private val items: List<SpotifyItem>,
    private val onToggle: (SpotifyItem, Boolean) -> Unit
) : RecyclerView.Adapter<SpotifyItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.playlist_name)
        val info: TextView = view.findViewById(R.id.playlist_info)
        val toggle: MaterialSwitch = view.findViewById(R.id.playlist_sync_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spotify_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.info.text = item.subtitle
        holder.toggle.isChecked = item.isSubscribed
        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            item.isSubscribed = isChecked
            onToggle(item, isChecked)
        }
    }

    override fun getItemCount() = items.size
}
