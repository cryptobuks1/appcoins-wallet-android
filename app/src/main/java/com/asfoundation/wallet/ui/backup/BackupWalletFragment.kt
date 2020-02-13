package com.asfoundation.wallet.ui.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.asf.wallet.R
import com.jakewharton.rxbinding2.view.RxView
import dagger.android.support.DaggerFragment
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_backup_wallet_layout.*
import kotlinx.android.synthetic.main.item_wallet_addr.*

class BackupWalletFragment : BackupWalletFragmentView, DaggerFragment() {

  private lateinit var fragmentContainer: ViewGroup
  private lateinit var presenter: BackupWalletFragmentPresenter

  companion object {
    private const val PARAM_WALLET_ADDR = "PARAM_WALLET_ADDR"

    @JvmStatic
    fun newInstance(walletAddress: String): BackupWalletFragment {
      val bundle = Bundle()
      bundle.putString(PARAM_WALLET_ADDR, walletAddress)
      val fragment = BackupWalletFragment()
      fragment.arguments = bundle
      return fragment
    }
  }

  private val walletAddr: String by lazy {
    if (arguments!!.containsKey(PARAM_WALLET_ADDR)) {
      arguments!!.getString(PARAM_WALLET_ADDR)
    } else {
      throw IllegalArgumentException("Wallet address not available")
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View? {
    fragmentContainer = container!!
    return inflater.inflate(R.layout.fragment_backup_wallet_layout, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    amount.text = "$1.5k"
    address.text = walletAddr
  }

  override fun getBackupButton(): Observable<String> {
    return RxView.clicks(backup_btn).map { password.text.toString() }
  }
}
