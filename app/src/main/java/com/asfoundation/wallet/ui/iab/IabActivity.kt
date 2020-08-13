package com.asfoundation.wallet.ui.iab

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import com.appcoins.wallet.billing.AppcoinsBillingBinder
import com.appcoins.wallet.billing.AppcoinsBillingBinder.Companion.EXTRA_BDS_IAP
import com.appcoins.wallet.billing.repository.entity.TransactionData
import com.asf.wallet.R
import com.asfoundation.wallet.backup.BackupNotificationUtils
import com.asfoundation.wallet.billing.adyen.AdyenPaymentFragment
import com.asfoundation.wallet.billing.adyen.PaymentType
import com.asfoundation.wallet.billing.analytics.BillingAnalytics
import com.asfoundation.wallet.entity.TransactionBuilder
import com.asfoundation.wallet.navigator.UriNavigator
import com.asfoundation.wallet.topup.TopUpActivity
import com.asfoundation.wallet.ui.AuthenticationPromptActivity
import com.asfoundation.wallet.ui.BaseActivity
import com.asfoundation.wallet.ui.PaymentNavigationData
import com.asfoundation.wallet.ui.iab.IabInteract.Companion.PRE_SELECTED_PAYMENT_METHOD_KEY
import com.asfoundation.wallet.ui.iab.WebViewActivity.Companion.SUCCESS
import com.asfoundation.wallet.ui.iab.share.SharePaymentLinkFragment
import com.asfoundation.wallet.wallet_blocked.WalletBlockedInteract
import com.asfoundation.wallet.wallet_validation.dialog.WalletValidationDialogActivity
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxrelay2.PublishRelay
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_iab.*
import kotlinx.android.synthetic.main.iab_error_layout.*
import kotlinx.android.synthetic.main.support_error_layout.*
import java.math.BigDecimal
import java.util.*
import javax.inject.Inject

class IabActivity : BaseActivity(), IabView, UriNavigator {

  @Inject
  lateinit var billingAnalytics: BillingAnalytics

  @Inject
  lateinit var iabInteract: IabInteract

  @Inject
  lateinit var walletBlockedInteract: WalletBlockedInteract

  //ADICIONEI ISTO
  @Inject
  lateinit var paymentMethodsMapper: PaymentMethodsMapper

  private lateinit var presenter: IabPresenter
  private var isBackEnable: Boolean = false
  private var transaction: TransactionBuilder? = null
  private var productName: String? = null
  private var isBds: Boolean = false
  private var results: PublishRelay<Uri>? = null
  private var developerPayload: String? = null
  private var uri: String? = null
  private var firstImpression = true
  private var paymentNavigationData: PaymentNavigationData? = null


  override fun onCreate(savedInstanceState: Bundle?) {
    AndroidInjection.inject(this)
    super.onCreate(savedInstanceState)
    results = PublishRelay.create()
    setContentView(R.layout.activity_iab)
    isBds = intent.getBooleanExtra(IS_BDS_EXTRA, false)
    developerPayload = intent.getStringExtra(DEVELOPER_PAYLOAD)
    uri = intent.getStringExtra(URI)
    transaction = intent.getParcelableExtra(TRANSACTION_EXTRA)
    isBackEnable = true
    if (savedInstanceState != null && savedInstanceState.containsKey(FIRST_IMPRESSION)) {
      firstImpression = savedInstanceState.getBoolean(FIRST_IMPRESSION)
    }
    presenter =
        IabPresenter(this, Schedulers.io(), AndroidSchedulers.mainThread(),
            CompositeDisposable(), billingAnalytics, firstImpression, iabInteract,
            walletBlockedInteract, transaction!!)
    if (savedInstanceState == null) presenter.handleGetSkuDetails()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == WEB_VIEW_REQUEST_CODE) {
      if (resultCode == WebViewActivity.FAIL) {
        sendPayPalConfirmationEvent("cancel")
        showPaymentMethodsView()
      } else if (resultCode == SUCCESS) {
        if (data?.scheme?.contains("adyencheckout") == true) {
          sendPaypalUrlEvent(data)
          if (getQueryParameter(data, "resultCode") == "cancelled")
            sendPayPalConfirmationEvent("cancel")
          else
            sendPayPalConfirmationEvent("buy")
        }
        results!!.accept(Objects.requireNonNull(data!!.data, "Intent data cannot be null!"))
      }
    } else if (requestCode == WALLET_VALIDATION_REQUEST_CODE) {
      var errorMessage = data?.getIntExtra(ERROR_MESSAGE, 0)
      if (errorMessage == null || errorMessage == 0) {
        errorMessage = R.string.unknown_error

      }
      presenter.handleWalletBlockedCheck(errorMessage)
    } else if (requestCode == AUTHENTICATION_REQUEST_CODE) {
      if (resultCode == AuthenticationPromptActivity.RESULT_OK) {
        navigateToPayment()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    //The present is set here due to the Can not perform this action after onSaveInstanceState
    //This assures that doesn't
    presenter.present()
  }

  override fun onBackPressed() {
    if (isBackEnable) {
      Bundle().apply {
        putInt(RESPONSE_CODE, RESULT_USER_CANCELED)
        close(this)
      }
      super.onBackPressed()
    }
  }

  override fun disableBack() {
    isBackEnable = false
  }

  override fun enableBack() {
    isBackEnable = true
  }

  override fun finishActivity(data: Bundle) {
    presenter.savePreselectedPaymentMethod(data)
    data.remove(PRE_SELECTED_PAYMENT_METHOD_KEY)
    setResult(Activity.RESULT_OK, Intent().putExtras(data))
    finish()
  }

  override fun showBackupNotification(walletAddress: String) {
    BackupNotificationUtils.showBackupNotification(this, walletAddress)
  }

  override fun finish(bundle: Bundle) {
    if (bundle.getInt(AppcoinsBillingBinder.RESPONSE_CODE) == AppcoinsBillingBinder.RESULT_OK) {
      presenter.handleBackupNotifications(bundle)
    } else {
      finishActivity(bundle)
    }
  }

  override fun finishWithError() {
    setResult(Activity.RESULT_CANCELED)
    finish()
  }

  override fun close(bundle: Bundle?) {
    val intent = Intent()
    bundle?.let { intent.putExtras(bundle) }
    setResult(Activity.RESULT_CANCELED, intent)
    finish()
  }

  override fun navigateToWebViewAuthorization(url: String) {
    startActivityForResult(WebViewActivity.newIntent(this, url), WEB_VIEW_REQUEST_CODE)
  }

  override fun showWalletValidation(@StringRes error: Int) {
    fragment_container.visibility = View.GONE
    val intent = WalletValidationDialogActivity.newIntent(this, error)
        .apply { intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    startActivityForResult(intent, WALLET_VALIDATION_REQUEST_CODE)
  }

  override fun showOnChain(amount: BigDecimal, isBds: Boolean, bonus: String,
                           gamificationLevel: Int) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, OnChainBuyFragment.newInstance(createBundle(amount),
            intent.data!!.toString(), isBds, transaction, bonus, gamificationLevel))
        .commit()
  }

  override fun showAdyenPayment(amount: BigDecimal, currency: String?, isBds: Boolean,
                                paymentType: PaymentType, bonus: String?, isPreselected: Boolean,
                                iconUrl: String?, gamificationLevel: Int) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container,
            AdyenPaymentFragment.newInstance(transaction!!.type, paymentType, transaction!!.domain,
                getOrigin(isBds), intent.dataString, transaction!!.amount(), amount, currency,
                bonus, isPreselected, gamificationLevel, getSkuDescription()))
        .commit()
  }

  override fun showAppcoinsCreditsPayment(appcAmount: BigDecimal, gamificationLevel: Int) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container,
            AppcoinsRewardsBuyFragment.newInstance(appcAmount, transaction!!, intent.data!!
                .toString(), isBds, gamificationLevel))
        .commit()
  }

  override fun showLocalPayment(domain: String, skuId: String?, originalAmount: String?,
                                currency: String?, bonus: String?, selectedPaymentMethod: String,
                                developerAddress: String, type: String, amount: BigDecimal,
                                callbackUrl: String?, orderReference: String?, payload: String?,
                                paymentMethodIconUrl: String, paymentMethodLabel: String,
                                gamificationLevel: Int) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container,
            LocalPaymentFragment.newInstance(domain, skuId, originalAmount, currency, bonus,
                selectedPaymentMethod, developerAddress, type, amount, callbackUrl, orderReference,
                payload, paymentMethodIconUrl, paymentMethodLabel, gamificationLevel))
        .commit()
  }

  override fun showPaymentMethodsView() {
    val isDonation = TransactionData.TransactionType.DONATION.name
        .equals(transaction?.type, ignoreCase = true)
    presenter.handlePurchaseStartAnalytics(transaction)
    layout_error.visibility = View.GONE
    fragment_container.visibility = View.VISIBLE
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, PaymentMethodsFragment.newInstance(transaction,
            getSkuDescription(), isBds, isDonation, developerPayload, uri,
            intent.dataString))
        .commit()
  }

  override fun showShareLinkPayment(domain: String, skuId: String?, originalAmount: String?,
                                    originalCurrency: String?, amount: BigDecimal, type: String,
                                    selectedPaymentMethod: String) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container,
            SharePaymentLinkFragment.newInstance(domain, skuId, originalAmount, originalCurrency,
                amount, type, selectedPaymentMethod))
        .commit()
  }

  override fun showMergedAppcoins(fiatAmount: BigDecimal, currency: String, bonus: String,
                                  appcEnabled: Boolean, creditsEnabled: Boolean, isBds: Boolean,
                                  isDonation: Boolean, gamificationLevel: Int,
                                  disabledReasonAppc: Int?, disabledReasonCredits: Int?) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container,
            MergedAppcoinsFragment.newInstance(fiatAmount, currency, bonus, transaction!!.domain,
                productName, transaction!!.amount(), appcEnabled, creditsEnabled, isBds,
                isDonation, transaction!!.skuId, transaction!!.type, gamificationLevel,
                disabledReasonAppc, disabledReasonCredits))
        .commit()
  }

  override fun showEarnAppcoins(domain: String, skuId: String?, amount: BigDecimal,
                                type: String) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container,
            EarnAppcoinsFragment.newInstance(domain, skuId, amount, type))
        .commit()
  }

  override fun showUpdateRequiredView() {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, IabUpdateRequiredFragment())
        .commit()
  }

  override fun showError(@StringRes error: Int) {
    fragment_container.visibility = View.GONE
    layout_error.visibility = View.VISIBLE
    error_message.text = getText(error)
  }

  override fun getSupportClicks(): Observable<Any> =
      Observable.merge(RxView.clicks(layout_support_logo), RxView.clicks(layout_support_icn))

  override fun errorDismisses() = RxView.clicks(error_dismiss)

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    presenter.onSaveInstance(outState)
  }

  private fun getOrigin(isBds: Boolean): String? {
    return if (transaction!!.origin == null) {
      if (isBds) BDS else null
    } else {
      transaction!!.origin
    }
  }

  private fun createBundle(amount: BigDecimal): Bundle {
    return Bundle().apply {
      putSerializable(TRANSACTION_AMOUNT, amount)
      putString(APP_PACKAGE, transaction!!.domain)
      putString(PRODUCT_NAME, intent.extras!!.getString(PRODUCT_NAME))
      putString(TRANSACTION_DATA, intent.dataString)
      putString(DEVELOPER_PAYLOAD, transaction!!.payload)
    }
  }

  fun isBds() = intent.getBooleanExtra(EXTRA_BDS_IAP, false)

  override fun updateTransaction(title: String?, price: BigDecimal) {
    transaction?.amount(price)
    productName = title
  }


  override fun navigateToUri(url: String) {
    navigateToWebViewAuthorization(url)
  }

  override fun uriResults() = results

  override fun launchIntent(intent: Intent) {
    startActivity(intent)
  }

  override fun lockRotation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
  }

  override fun unlockRotation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  }

  override fun onPause() {
    presenter.stop()
    super.onPause()
  }

  private fun sendPayPalConfirmationEvent(action: String) {
    billingAnalytics.sendPaymentConfirmationEvent(transaction?.domain, transaction?.skuId,
        transaction?.amount()
            .toString(), "paypal",
        transaction?.type, action)
  }

  private fun sendPaypalUrlEvent(data: Intent) {
    val amountString = transaction?.amount()
        .toString()
    billingAnalytics.sendPaypalUrlEvent(transaction?.domain, transaction?.skuId,
        amountString, "PAYPAL", getQueryParameter(data, "type"),
        getQueryParameter(data, "resultCode"), data.dataString)
  }

  private fun getQueryParameter(data: Intent, parameter: String): String? {
    return Uri.parse(data.dataString)
        .getQueryParameter(parameter)
  }

  private fun getSkuDescription(): String {
    return when {
      productName.isNullOrEmpty()
          .not() -> productName!!
      transaction != null && transaction!!.skuId.isNullOrEmpty()
          .not() -> transaction!!.skuId
      else -> throw IllegalArgumentException("productName and sku not found")
    }
  }


  override fun showAuthenticationActivity(paymentNavigationData: PaymentNavigationData) {
    this.paymentNavigationData = paymentNavigationData
    val intent = AuthenticationPromptActivity.newIntent(this)
    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
    startActivityForResult(intent, TopUpActivity.AUTHENTICATION_REQUEST_CODE)
  }


  override fun navigateToPayment() {
    when (paymentMethodsMapper.map(paymentNavigationData!!.selectedPaymentId)) {
      PaymentMethodsView.SelectedPaymentMethod.PAYPAL -> showAdyenPayment(
          paymentNavigationData!!.fiatAmount,
          paymentNavigationData!!.fiatCurrency, isBds,
          PaymentType.PAYPAL, paymentNavigationData!!.bonusMessageValue, false, null,
          paymentNavigationData!!.gamificationLevel)
      PaymentMethodsView.SelectedPaymentMethod.CREDIT_CARD -> showAdyenPayment(
          paymentNavigationData!!.fiatAmount,
          paymentNavigationData!!.fiatCurrency, isBds,
          PaymentType.CARD, paymentNavigationData!!.bonusMessageValue, false, null,
          paymentNavigationData!!.gamificationLevel)
      PaymentMethodsView.SelectedPaymentMethod.APPC -> showOnChain(
          paymentNavigationData!!.fiatAmount,
          isBds, paymentNavigationData!!.bonusMessageValue,
          paymentNavigationData!!.gamificationLevel)

      PaymentMethodsView.SelectedPaymentMethod.APPC_CREDITS -> showAppcoinsCreditsPayment(
          transaction!!.amount(), paymentNavigationData!!.gamificationLevel)

      PaymentMethodsView.SelectedPaymentMethod.SHARE_LINK -> {
        val isOneStep: Boolean = transaction!!.type
            .equals("INAPP_UNMANAGED", ignoreCase = true)
        showShareLinkPayment(transaction!!.domain, transaction!!.skuId,
            if (isOneStep) transaction!!.originalOneStepValue else null,
            if (isOneStep) transaction!!.originalOneStepCurrency else null,
            transaction!!.amount(),
            transaction!!.type, paymentNavigationData!!.selectedPaymentId)
      }

      PaymentMethodsView.SelectedPaymentMethod.LOCAL_PAYMENTS -> {
        val isOneStep: Boolean = transaction!!.type
            .equals("INAPP_UNMANAGED", ignoreCase = true)
        showLocalPayment(transaction!!.domain, transaction!!.skuId,
            if (isOneStep) transaction!!.originalOneStepValue else null,
            if (isOneStep) transaction!!.originalOneStepCurrency else null,
            paymentNavigationData!!.bonusMessageValue,
            paymentNavigationData!!.selectedPaymentId, transaction!!.toAddress(),
            transaction!!.type,
            transaction!!.amount(), transaction!!.callbackUrl,
            transaction!!.orderReference, transaction!!.payload,
            paymentNavigationData!!.selectedPaymentIcon!!,
            paymentNavigationData!!.selectedPaymentLabel!!,
            paymentNavigationData!!.gamificationLevel)
      }

    }


  }


  companion object {

    const val URI = "uri"
    const val RESPONSE_CODE = "RESPONSE_CODE"
    const val RESULT_USER_CANCELED = 1
    const val FIRST_IMPRESSION = "first_impression"
    const val APP_PACKAGE = "app_package"
    const val TRANSACTION_EXTRA = "transaction_extra"
    const val PRODUCT_NAME = "product_name"
    const val TRANSACTION_DATA = "transaction_data"
    const val TRANSACTION_HASH = "transaction_hash"
    const val TRANSACTION_AMOUNT = "transaction_amount"
    const val DEVELOPER_PAYLOAD = "developer_payload"
    const val BDS = "BDS"
    const val WEB_VIEW_REQUEST_CODE = 1234
    const val BLOCKED_WARNING_REQUEST_CODE = 12345
    const val WALLET_VALIDATION_REQUEST_CODE = 12346
    const val AUTHENTICATION_REQUEST_CODE = 33
    const val IS_BDS_EXTRA = "is_bds_extra"
    const val ERROR_MESSAGE = "error_message"

    @JvmStatic
    fun newIntent(activity: Activity, previousIntent: Intent, transaction: TransactionBuilder,
                  isBds: Boolean?, developerPayload: String?): Intent {
      return Intent(activity, IabActivity::class.java)
          .apply {
            data = previousIntent.data
            if (previousIntent.extras != null) {
              putExtras(previousIntent.extras!!)
            }
            putExtra(TRANSACTION_EXTRA, transaction)
            putExtra(IS_BDS_EXTRA, isBds)
            putExtra(DEVELOPER_PAYLOAD, developerPayload)
            putExtra(URI, data!!.toString())
            putExtra(APP_PACKAGE, transaction.domain)
          }
    }

  }
}