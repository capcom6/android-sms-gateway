package me.capcom.smsgateway.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import me.capcom.smsgateway.databinding.DialogEncryptionBinding


class EncryptionDialog : DialogFragment() {

    private var _binding: DialogEncryptionBinding? = null
    private val binding get() = _binding!!

    private val requestKey by lazy {
        requireNotNull(requireArguments().getString(ARG_REQUEST_KEY))
    }
    private val passphrase by lazy {
        requireArguments().getString(ARG_PASSPHRASE) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogEncryptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextEncryption.setText(passphrase)

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        binding.buttonOk.setOnClickListener {
            val passphrase = binding.editTextEncryption.text.toString()
            setFragmentResult(requestKey, Bundle().apply { putString(ARG_PASSPHRASE, passphrase) })
            dismiss()
        }
        
//        binding.editTextEncryption.requestFocus()
//        WindowCompat.getInsetsController(requireActivity().window, binding.editTextEncryption)
//            .show(WindowInsetsCompat.Type.ime())
    }

    companion object {
        private const val ARG_REQUEST_KEY = "requestKey"
        private const val ARG_PASSPHRASE = "passphrase"
        fun newInstance(requestKey: String, passphrase: String) = EncryptionDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
                putString(ARG_PASSPHRASE, passphrase)
            }
        }

        fun getPassphrase(bundle: Bundle) = bundle.getString(ARG_PASSPHRASE)
    }
}