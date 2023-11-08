package me.capcom.smsgateway.ui.adapters

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.databinding.ItemMessageBinding

class MessagesAdapter :
    ListAdapter<Message, MessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)

        holder.bind(message)
    }

    class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            Log.d("MessageViewHolder", message.id)
            binding.textViewId.text = message.id
            binding.textViewDate.text = message.createdAt.toString()
            val tintColor = when (message.state) {
                Message.State.Pending -> Color.parseColor("#FFBB86FC")
                Message.State.Processed -> Color.parseColor("#FF6200EE")
                Message.State.Sent -> Color.parseColor("#FF3700B3")
                Message.State.Delivered -> Color.parseColor("#FF03DAC5")
                Message.State.Failed -> Color.parseColor("#FF018786")
            }

            binding.imageViewState.setColorFilter(tintColor)
        }

        companion object {
            fun create(parent: ViewGroup): MessageViewHolder {
                return MessageViewHolder(
                    ItemMessageBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                )
            }
        }
    }

    class MessageDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}