package com.asfoundation.wallet.di

import com.asfoundation.wallet.billing.analytics.WalletsEventSender
import com.asfoundation.wallet.interact.TransactionViewInteract
import com.asfoundation.wallet.navigator.TransactionViewNavigator
import com.asfoundation.wallet.router.*
import com.asfoundation.wallet.support.SupportRepository
import com.asfoundation.wallet.transactions.TransactionsAnalytics
import com.asfoundation.wallet.ui.AppcoinsApps
import com.asfoundation.wallet.util.CurrencyFormatUtils
import com.asfoundation.wallet.viewmodel.TransactionsViewModelFactory
import dagger.Module
import dagger.Provides

@Module
internal class TransactionsModule {
  @Provides
  fun provideTransactionsViewModelFactory(applications: AppcoinsApps,
                                          analytics: TransactionsAnalytics,
                                          transactionViewNavigator: TransactionViewNavigator,
                                          transactionViewInteract: TransactionViewInteract,
                                          walletsEventSender: WalletsEventSender,
                                          supportRepository: SupportRepository,
                                          formatter: CurrencyFormatUtils): TransactionsViewModelFactory {
    return TransactionsViewModelFactory(applications, analytics, transactionViewNavigator,
        transactionViewInteract, walletsEventSender, supportRepository, formatter)
  }

  @Provides
  fun provideTransactionsViewNavigator(settingsRouter: SettingsRouter, sendRouter: SendRouter,
                                       transactionDetailRouter: TransactionDetailRouter,
                                       myAddressRouter: MyAddressRouter,
                                       balanceRouter: BalanceRouter,
                                       externalBrowserRouter: ExternalBrowserRouter,
                                       topUpRouter: TopUpRouter): TransactionViewNavigator {
    return TransactionViewNavigator(settingsRouter, sendRouter, transactionDetailRouter,
        myAddressRouter, balanceRouter, externalBrowserRouter, topUpRouter)
  }

  @Provides
  fun provideSettingsRouter() = SettingsRouter()

  @Provides
  fun provideSendRouter() = SendRouter()

  @Provides
  fun provideSendRouterTopUpRouter() = TopUpRouter()

  @Provides
  fun provideTransactionDetailRouter() = TransactionDetailRouter()

  @Provides
  fun provideMyAddressRouter() = MyAddressRouter()

  @Provides
  fun provideMyTokensRouter() = BalanceRouter()

  @Provides
  fun provideExternalBrowserRouter() = ExternalBrowserRouter()

  @Provides
  fun provideAirdropRouter() = AirdropRouter()
}