package me.capcom.smsgateway.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.DialogPasswordPromptBinding

class PasswordPromptDialogFragment : DialogFragment() {

    private var _binding: DialogPasswordPromptBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPasswordPromptBinding.inflate(layoutInflater)

        arguments?.getString(ARG_MESSAGE)?.let {
            binding.textMessage.text = it
        }

        return AlertDialog.Builder(requireActivity())
            .apply {
                setView(binding.root)
                setPositiveButton(R.string.btn_continue) { _, _ ->
                    val password = binding.editPassword.text.toString()
                    setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(KEY_PASSWORD, password)
                        }
                    )
                }
                setNegativeButton(R.string.btn_cancel) { _, _ ->
                    setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(KEY_PASSWORD, "")
                            putBoolean(KEY_CANCELED, true)
                        }
                    )
                }
            }
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "PasswordPromptDialogFragment"
        private const val ARG_MESSAGE = "message"
        private const val KEY_PASSWORD = "password"
        private const val KEY_CANCELED = "canceled"

        fun newInstance(message: String? = null): PasswordPromptDialogFragment {
            return PasswordPromptDialogFragment().apply {
                arguments = Bundle().apply {
                    message?.let { putString(ARG_MESSAGE, it) }
                }
            }
        }

        fun getPassword(data: Bundle): String? {
            return if (data.getBoolean(KEY_CANCELED, false)) {
                null
            } else {
                data.getString(KEY_PASSWORD)
            }
        }
    }
}