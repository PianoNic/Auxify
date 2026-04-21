package org.oxycblt.auxio.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R

class SyncDashboardFragment : Fragment() {

    private lateinit var subscriptionManager: SubscriptionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_sync_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        subscriptionManager = SubscriptionManager(requireContext())

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val statusText = view.findViewById<TextView>(R.id.sync_status_text)
        val progressBar = view.findViewById<LinearProgressIndicator>(R.id.sync_progress)
        val subsList = view.findViewById<RecyclerView>(R.id.subscriptions_list)
        val browseBtn = view.findViewById<MaterialButton>(R.id.btn_browse_spotify)
        val syncBtn = view.findViewById<MaterialButton>(R.id.btn_sync_now)

        subsList.layoutManager = LinearLayoutManager(requireContext())

        browseBtn.setOnClickListener {
            findNavController().navigate(R.id.spotify_library_fragment)
        }

        syncBtn.setOnClickListener {
            SyncWorker.syncNow(requireContext())
            statusText.text = "Sync started..."
        }

        loadSubscriptions(statusText, progressBar, subsList)
    }

    private fun loadSubscriptions(
        statusText: TextView,
        progressBar: LinearProgressIndicator,
        subsList: RecyclerView
    ) {
        lifecycleScope.launch {
            val subs = subscriptionManager.getSubscriptions()
            val db = SyncDatabaseProvider.get(requireContext())

            if (subs.isEmpty()) {
                statusText.text = "No subscriptions yet. Browse your Spotify library to subscribe."
                progressBar.visibility = View.GONE
                return@launch
            }

            var totalPending = 0
            var totalDone = 0
            var totalTracks = 0

            val subData = subs.map { sub ->
                val tracks = db.syncedTrackDao().getBySubscription(sub.spotifyId)
                val done = tracks.count { it.status == "done" }
                val pending = tracks.count { it.status == "pending" }
                val failed = tracks.count { it.status == "failed" }
                totalPending += pending
                totalDone += done
                totalTracks += tracks.size

                SubDisplayItem(
                    sub = sub,
                    downloaded = done,
                    pending = pending,
                    failed = failed,
                    total = tracks.size
                )
            }

            statusText.text = "$totalDone/$totalTracks tracks downloaded, $totalPending pending"

            if (totalTracks > 0) {
                progressBar.visibility = View.VISIBLE
                progressBar.max = totalTracks
                progressBar.progress = totalDone
            } else {
                progressBar.visibility = View.GONE
            }

            subsList.adapter = SubscriptionAdapter(subData) { subItem ->
                lifecycleScope.launch {
                    subscriptionManager.unsubscribe(subItem.sub.spotifyId)
                    loadSubscriptions(statusText, progressBar, subsList)
                }
            }
        }
    }
}

data class SubDisplayItem(
    val sub: Subscription,
    val downloaded: Int,
    val pending: Int,
    val failed: Int,
    val total: Int
)

class SubscriptionAdapter(
    private val items: List<SubDisplayItem>,
    private val onRemove: (SubDisplayItem) -> Unit
) : RecyclerView.Adapter<SubscriptionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.sub_name)
        val status: TextView = view.findViewById(R.id.sub_status)
        val remove: ImageButton = view.findViewById(R.id.sub_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.sub.name
        val statusParts = mutableListOf<String>()
        statusParts.add("${item.downloaded}/${item.total} downloaded")
        if (item.pending > 0) statusParts.add("${item.pending} pending")
        if (item.failed > 0) statusParts.add("${item.failed} failed")
        holder.status.text = statusParts.joinToString(" · ")
        holder.remove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount() = items.size
}
