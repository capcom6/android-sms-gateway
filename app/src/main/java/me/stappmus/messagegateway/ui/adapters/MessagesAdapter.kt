package me.stappmus.messagegateway.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.stappmus.messagegateway.data.entities.Message
import me.stappmus.messagegateway.databinding.ItemMessageBinding
import me.stappmus.messagegateway.ui.styles.color
import java.text.DateFormat
import java.util.Date

class MessagesAdapter(
    private val onItemClickListener: OnItemClickListener<Message>
) :
    ListAdapter<Message, MessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder.create(parent).also { holder ->
            holder.itemView.setOnClickListener {
                val message = getItem(holder.adapterPosition)
                onItemClickListener.onItemClick(message)
            }
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)

        holder.bind(message)
    }

    class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.textViewId.text = message.id
            binding.textViewDate.text =
                DateFormat.getDateTimeInstance().format(Date(message.createdAt))
            binding.textViewState.text = message.state.name
            binding.imageViewState.setColorFilter(message.state.color)
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

    interface OnItemClickListener<T> {
        fun onItemClick(item: T)
    }
}