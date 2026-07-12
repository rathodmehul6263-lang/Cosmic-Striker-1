package com.example

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * A highly polished, high-performance RecyclerView adapter for displaying
 * the online global leaderboard. This adapter is entirely self-contained and builds
 * its layouts programmatically to ensure it is plug-and-play without XML files.
 */
class LeaderboardAdapter(
    private val currentUserId: String? = null
) : ListAdapter<LeaderboardUser, LeaderboardAdapter.LeaderboardViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        // Root container with cybernetic layout padding
        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
        }

        // Left layout to group Rank, Avatar Emoji, and Player Names
        val leftLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Rank indicator
        val rankTextView = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            minWidth = dpToPx(55) // Minimum width to align cleanly
        }
        leftLayout.addView(rankTextView)

        // Avatar placeholder
        val avatarView = TextView(context).apply {
            textSize = 20f
            text = "👤"
            setPadding(0, 0, dpToPx(8), 0)
        }
        leftLayout.addView(avatarView)

        // Sub-layout for player name and UID
        val detailsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameTextView = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        detailsLayout.addView(nameTextView)

        val uidTextView = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.GRAY)
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        detailsLayout.addView(uidTextView)

        leftLayout.addView(detailsLayout)
        container.addView(leftLayout)

        // Right layout for Level and Kills records
        val rightLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        val levelTextView = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.rgb(0, 240, 255)) // Glowing Cyan
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        rightLayout.addView(levelTextView)

        val killsTextView = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.rgb(255, 0, 128)) // Cosmic Pink
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        rightLayout.addView(killsTextView)

        container.addView(rightLayout)

        return LeaderboardViewHolder(
            itemView = container,
            rankTextView = rankTextView,
            nameTextView = nameTextView,
            uidTextView = uidTextView,
            levelTextView = levelTextView,
            killsTextView = killsTextView
        )
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user, currentUserId)
    }

    class LeaderboardViewHolder(
        itemView: View,
        private val rankTextView: TextView,
        private val nameTextView: TextView,
        private val uidTextView: TextView,
        private val levelTextView: TextView,
        private val killsTextView: TextView
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(user: LeaderboardUser, currentUserId: String?) {
            // Rank presentation color scheme
            val rankColor = when (user.rank) {
                1 -> Color.rgb(255, 215, 0)    // Gold
                2 -> Color.rgb(192, 192, 192)  // Silver
                3 -> Color.rgb(205, 127, 50)   // Bronze
                else -> Color.rgb(143, 160, 221) // Cosmic Blue
            }

            rankTextView.setTextColor(rankColor)
            rankTextView.text = when (user.rank) {
                1 -> "🏆 #1"
                2 -> "🥈 #2"
                3 -> "🥉 #3"
                else -> "   #${user.rank}"
            }

            val isCurrentUser = !currentUserId.isNullOrEmpty() && user.uid == currentUserId
            if (isCurrentUser) {
                // Highlighting row for the logged-in user
                itemView.setBackgroundColor(Color.argb(30, 0, 240, 255))
                nameTextView.setTextColor(Color.rgb(0, 240, 255))
                nameTextView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            } else {
                // Normal background shade
                itemView.setBackgroundColor(Color.argb(8, 255, 255, 255))
                nameTextView.setTextColor(Color.WHITE)
                nameTextView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            nameTextView.text = user.playerName
            uidTextView.text = "Player ID: ${user.playerId}"
            levelTextView.text = "Score: ${user.score}"
            killsTextView.text = "Lvl ${user.level} | Kills: ${user.kills}"
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LeaderboardUser>() {
        override fun areItemsTheSame(oldItem: LeaderboardUser, newItem: LeaderboardUser): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: LeaderboardUser, newItem: LeaderboardUser): Boolean {
            return oldItem == newItem
        }
    }
}
