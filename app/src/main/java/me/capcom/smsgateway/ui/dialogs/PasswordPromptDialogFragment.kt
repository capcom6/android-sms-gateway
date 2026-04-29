package me.capcom.smsgateway.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
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

        val dialog = AlertDialog.Builder(requireActivity())
            .apply {
                setView(binding.root)
                setPositiveButton(R.string.btn_continue, null)
                setNegativeButton(R.string.btn_cancel) { _, _ ->
                    sendCanceledResult()
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = binding.editPassword.text?.toString().orEmpty()
                if (password.isEmpty()) {
                    binding.editPasswordLayout.error =
                        getString(R.string.password_must_not_be_empty)
                    return@setOnClickListener
                }
                binding.editPasswordLayout.error = null
                setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply {
                        putString(KEY_PASSWORD, password)
                        putString(KEY_ACTION, arguments?.getString(ARG_ACTION))
                        putString(KEY_PAYLOAD, arguments?.getString(ARG_PAYLOAD))
                    }
                )
                dialog.dismiss()
            }
        }
        return dialog
    }

    private fun sendCanceledResult() {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_PASSWORD, "")
                putBoolean(KEY_CANCELED, true)
            }
        )
    }

    override fun onCancel(dialog: DialogInterface) {
        sendCanceledResult()
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "PasswordPromptDialogFragment"
        const val ACTION_CHANGE_PASSWORD = "change_password"
        const val ACTION_LOGIN_CODE = "login_code"

        private const val ARG_MESSAGE = "message"
        private const val ARG_ACTION = "action"
        private const val ARG_PAYLOAD = "payload"

        private const val KEY_PASSWORD = "password"
        private const val KEY_CANCELED = "canceled"
        private const val KEY_ACTION = "action"
        private const val KEY_PAYLOAD = "payload"

        fun newInstance(
            message: String? = null,
            action: String? = null,
            payload: String? = null
        ): PasswordPromptDialogFragment {
            return PasswordPromptDialogFragment().apply {
                arguments = Bundle().apply {
                    message?.let { putString(ARG_MESSAGE, it) }
                    action?.let { putString(ARG_ACTION, it) }
                    payload?.let { putString(ARG_PAYLOAD, it) }
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

        fun getAction(data: Bundle): String? {
            return data.getString(KEY_ACTION)
        }

        fun getPayload(data: Bundle): String? {
            return data.getString(KEY_PAYLOAD)
        }
    }
}