package com.example.budget.model.utils

import android.util.Log
import com.example.budget.model.constants.BudgetGroupEnum
import com.example.budget.model.database.converters.Converters
import com.example.budget.model.domain.Bank
import com.example.budget.model.domain.BankAccount
import com.example.budget.model.domain.BudgetEntry
import com.example.budget.model.domain.BudgetGroup
import com.example.budget.model.domain.CardType
import com.example.budget.model.domain.OperationType
import com.example.budget.model.domain.Seller
import com.example.budget.model.domain.SmsData
import com.example.budget.repository.DBRepository
import java.util.Date
import java.util.regex.Pattern


class SmsDataMapper(val dbRepository: DBRepository) {
    companion object {
        const val TAG = "SmsDataMapper"
        val CURRENCYLIST = listOf("RUB", "USD")
        const val SPACE = " "
        const val COMMA = ","
        const val DOT = "."
    }

    private lateinit var banks: MutableList<Bank>
    private lateinit var sellers: MutableList<Seller>
    private lateinit var bankAccounts: MutableList<BankAccount>
    private lateinit var budgetGroups: MutableList<BudgetGroup>

    val converter = Converters(dbRepository)

    suspend fun convertSMSToBudgetEntry(sms: SmsData): BudgetEntry? {

        val budgetEntry: BudgetEntry?

        Log.i(TAG, "convertSMSToBudgetEntry: sms.adress = ${sms.sender}")
        Log.i(TAG, "convertSMSToBudgetEntry: sms.body = ${sms.body}")
        Log.i(TAG, "convertSMSToBudgetEntry: sms.date = ${Date(sms.date)}")

        val bank = banks.find { it.smsAddress.equals(sms.sender) }
        if (bank == null) {
            Log.i(TAG, "convertSMSToBudgetEntry: bank wasn't find for ${sms.sender}")
            return null
        }


        var operationType = OperationType.EXPENSE
        var pattern = Pattern.compile(bank.operationTypeEXPENSERegex)
        var matcher = pattern.matcher(sms.body)
        if (matcher.find()) {
            operationType = OperationType.EXPENSE
        } else {
            pattern = Pattern.compile(bank.operationTypeINCOMERegex)
            matcher = pattern.matcher(sms.body)
            if (matcher.find()) {
                operationType = OperationType.INCOME
            }
        }

        val cardpan = smsMatcher(bank.cardPanRegex, sms.body)

        val amount =
            smsMatcher(bank.operationAmountRegex, sms.body)?.replace(COMMA, DOT)?.replace(SPACE, "")
                ?.toDouble()

        val balance =
            smsMatcher(bank.balanceRegex, sms.body)?.replace(COMMA, DOT)?.replace(SPACE, "")
                ?.toDouble()

        val seller = smsMatcher(bank.sellerNameRegex, sms.body)

        if (cardpan.isNullOrEmpty() or !(amount != null) or !(balance != null) or seller.isNullOrEmpty()) {
            Log.i(TAG, "convertSMSToBudgetEntry: null $cardpan; $amount; $balance; $seller")
            return null
        } else {

            Log.i(TAG, "convertSMSToBudgetEntry: getKart $cardpan; $amount; $balance; $seller")

            bankAccounts.filter { it.cardPan.equals(cardpan) }.let { list ->
                if (list.isEmpty()) {
                    val bankAccount = BankAccount(
                        0,
                        cardPan = cardpan!!,
                        bankSMSAddress = sms.sender,
                        cardType = CardType.NOTYPE,
                        cardLimit = 0.0,
                        balance = balance!!,
                    )?.let {
                        bankAccounts.add(it)
                        converter.bankAccountConverter(it)
                            ?.let { it1 -> dbRepository.insertBankAccountEntity(it1) }
                    }

                } else {
                    list.first().balance = balance!!
                    converter.bankAccountConverter(list.first())
                        ?.let { dbRepository.update(it) }//Update not insert
                }
            }


            var budgetGroup = BudgetGroupEnum.НЕ_ОПРЕДЕЛЕНО

            sellers.filter { it.name.equals(seller) }.let { sellerList ->
                if (sellerList.isEmpty()) {
                    Seller(seller!!, BudgetGroupEnum.НЕ_ОПРЕДЕЛЕНО)?.let {
                        sellers.add(it)
                        converter.sellerConverter(it)
                            ?.let { it1 -> dbRepository.insertSellerEntity(it1) }
                    }
                } else {
                    budgetGroup = sellerList.first().budgetGroupName
                }
            }


            budgetEntry = BudgetEntry(
                date = Date(sms.date),
                operationType = operationType,
                cardSPan = cardpan ?: "NoCard",
                bankSMSAdress = sms.sender,
                note = "",
                operationAmount = amount ?: 0.0,
                sellerName = seller ?: "NoSeller",
                budgetGroup = budgetGroup,
            )
        }
        return budgetEntry
    }


    private fun smsMatcher(
        regex: String, strForMatch: String,
    ): String? {
        val pattern = regex.toRegex()
        pattern.find(strForMatch)?.let { match ->

            val value = match.groupValues[1]
            Log.i(TAG, "smsMatcher: group[1] = $value")
            return value
        }
        return null
        /*val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(strForMatch)
        if (matcher.find()) {
            Log.i(TAG, "smsMatcher: ${matcher.results(). .group()}")
            return matcher.group()
        } else {
            return null
        }*/
    }

    fun updateRules() {

    }

    fun banksUpdate(newBanks: List<Bank>) {
        banks = newBanks as MutableList<Bank>
    }

    fun updateSellers(newSellers: List<Seller>) {
        sellers = newSellers as MutableList<Seller>
    }

    fun updateBudgetGroup(newBudgetGroups: List<BudgetGroup>) {
        budgetGroups = newBudgetGroups as MutableList<BudgetGroup>
    }

    fun updateBankAccounts(newBankAcounts: List<BankAccount>) {
        bankAccounts = newBankAcounts as MutableList<BankAccount>
    }

    fun updateBanks(newList: List<Bank>) {
        banks = newList as MutableList<Bank>
    }


}