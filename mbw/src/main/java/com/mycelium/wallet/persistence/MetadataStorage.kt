/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.persistence

import android.content.Context
import android.text.TextUtils

import com.google.common.base.Optional
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.mrd.bitlib.model.Address
import com.mycelium.wapi.wallet.AddressUtils
import com.mycelium.wapi.wallet.GenericAddress
import com.mycelium.wapi.wallet.bch.coins.BchMain
import com.mycelium.wapi.wallet.bch.coins.BchTest
import com.mycelium.wapi.wallet.btc.coins.BitcoinMain
import com.mycelium.wapi.wallet.btc.coins.BitcoinTest
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.colu.coins.*
import com.mycelium.wapi.wallet.metadata.MetadataCategory
import com.mycelium.wapi.wallet.metadata.MetadataKeyCategory

import java.math.BigDecimal
import java.util.*

class MetadataStorage(context: Context) : GenericMetadataStorage(context) {
    val allAddressLabels: MutableMap<GenericAddress, String>
        get() {
            val entries = getKeysAndValuesByCategory(ADDRESSLABEL_CATEGORY)
            val addresses = HashMap<GenericAddress, String>()
            val addressesOfCointype = getKeysAndValuesByCategory(ADDRESSCOINTYPE_CATEGORY)
                    .filterKeys(entries::containsKey)
            var coinType: String
            for (e in entries.entries) {
                val value = e.value
                val key = e.key
                coinType = addressesOfCointype[key].toString()
                if (AddressUtils.from(coinTypeFromString(coinType), key) != null) {
                    addresses[AddressUtils.from(coinTypeFromString(coinType), key)] = value
                }
            }
            return addresses
        }

    val allGenericAddress: List<GenericAddress>
        get() {
            val entries = getKeysAndValuesByCategory(ADDRESSCOINTYPE_CATEGORY)
            val addresses = ArrayList<GenericAddress>()
            for (e in entries.entries) {
                val value = coinTypeFromString(e.value)
                val key = e.key
                addresses.add(AddressUtils.from(value, key))
            }
            return addresses
        }

    // if this is the first verified backup, remember the date
    var masterSeedBackupState: BackupState
        get() {
            return BackupState.fromString(
                    getKeyCategoryValueEntry(SEED_BACKUPSTATE, BackupState.UNKNOWN.toString())
            )
        }
        set(state) {
            storeKeyCategoryValueEntry(SEED_BACKUPSTATE, state.toString())
            if (state == BackupState.VERIFIED && masterSeedBackupState != BackupState.VERIFIED) {
                storeKeyCategoryValueEntry(SEED_BACKUPSTATE, (Calendar.getInstance().timeInMillis).toString())
            }
        }

    val masterKeyBackupAgeMs: Optional<Long>
        get() {
            val lastBackup = getKeyCategoryValueEntry(SEED_BACKUPSTATE)
            return if (lastBackup.isPresent) {
                Optional.of<Long>(Calendar.getInstance().timeInMillis - java.lang.Long.valueOf(lastBackup.get()))
            } else {
                Optional.absent<Long>()
            }
        }

    val resetPinStartBlockHeight: Optional<Int>
        get() {
            val resetIn = getKeyCategoryValueEntry(PIN_RESET_BLOCKHEIGHT)
            return if (resetIn.isPresent) {
                Optional.of<Int>(Integer.valueOf(resetIn.get()))
            } else {
                Optional.absent<Int>()
            }
        }

    val lastPinSetBlockheight: Optional<Int>
        get() {
            val lastSet = getKeyCategoryValueEntry(PIN_BLOCKHEIGHT)
            return if (lastSet.isPresent) {
                Optional.of<Int>(Integer.valueOf(lastSet.get()))
            } else {
                Optional.absent<Int>()
            }
        }

    val coinapultCurrencies: String
        get() {
            return getKeyCategoryValueEntry(COINAPULT.of("currencies"), "")
        }

    val coinapultLastFlush: Int
        get() {
            return try {
                Integer.valueOf(getKeyCategoryValueEntry(COINAPULT.of("flush"), "0"))
            } catch (ex: NumberFormatException) {
                0
            }

        }

    var coinapultMail: String
        get() {
            val mail = getKeyCategoryValueEntry(COINAPULT.of(EMAIL))
            return if (mail.isPresent) {
                mail.get()
            } else {
                ""
            }
        }
        set(mail) {
            storeKeyCategoryValueEntry(COINAPULT.of(EMAIL), mail)
        }

    val coluAssetIds: Iterable<String>
        get() {
            return Splitter.on(",").split(getKeyCategoryValueEntry(COLU.of("assetIds"), ""))
        }

    val allExchangeRates: Map<String, String>
        get() {
            return getKeysAndValuesByCategory(EXCHANGE_RATES_CATEGORY)
        }

    var swishCreditCardIsEnabled: Boolean
        get() {
            return getKeyCategoryValueEntry(SWISH_CREDIT_CARD_IS_ENABLED, "1") == "1"
        }
        set(enable) {
            storeKeyCategoryValueEntry(SWISH_CREDIT_CARD_IS_ENABLED, if (enable) "1" else "0")
        }

    var simplexIsEnabled: Boolean
        get() {
            return getKeyCategoryValueEntry(SIMPLEX_IS_ENABLED, "1") == "1"
        }
        set(enable) {
            storeKeyCategoryValueEntry(SIMPLEX_IS_ENABLED, if (enable) "1" else "0")
        }

    var changellyIsEnabled: Boolean
        get() {
            return getKeyCategoryValueEntry(CHANGELLY_IS_ENABLED, "1") == "1"
        }
        set(enable) {
            storeKeyCategoryValueEntry(CHANGELLY_IS_ENABLED, if (enable) "1" else "0")
        }

    val lastFullSync: Optional<Long>
        get() {
            val lastDateStr = getKeyCategoryValueEntry(SYNC_LAST_FULLSYNC)
            return if (lastDateStr.isPresent) {
                try {
                    Optional.of(java.lang.Long.parseLong(lastDateStr.get()))
                } catch (ex: NumberFormatException) {
                    Optional.absent<Long>()
                }

            } else {
                Optional.absent<Long>()
            }
        }

    var showBip44Path: Boolean
        get() {
            return getKeyCategoryValueEntry(SHOW_BIP44_PATH, "0") == "1"
        }
        set(show) {
            storeKeyCategoryValueEntry(SHOW_BIP44_PATH, if (show) "1" else "0")
        }

    fun storeTransactionLabel(txid: String, label: String) {
        if (!Strings.isNullOrEmpty(label)) {
            storeKeyCategoryValueEntry(TRANSACTION_LABEL_CATEGORY.of(txid), label)
        } else {
            // remove the transaction label
            deleteByKeyCategory(TRANSACTION_LABEL_CATEGORY.of(txid))
        }
    }

    fun getLabelByTransaction(txid: String): String {
        return getKeyCategoryValueEntry(TRANSACTION_LABEL_CATEGORY.of(txid), "")
    }

    fun getLabelByAccount(account: UUID): String {
        return getKeyCategoryValueEntry(ACCOUNTLABEL_CATEGORY.of(account.toString()), "")
    }

    fun getAccountByLabel(label: String): Optional<UUID> {
        val account = getFirstKeyForCategoryValue(ACCOUNTLABEL_CATEGORY, label)

        return if (account.isPresent) {
            Optional.of<UUID>(UUID.fromString(account.get()))
        } else {
            Optional.absent<UUID>()
        }
    }

    fun storeAccountLabel(account: UUID, label: String) {
        if (!Strings.isNullOrEmpty(label)) {
            storeKeyCategoryValueEntry(ACCOUNTLABEL_CATEGORY.of(account.toString()), label)
        }
    }

    // Removes all metadata (account label,...) from the database
    fun deleteAccountMetadata(account: UUID) {
        deleteAllByKey(account.toString())
    }

    private fun coinTypeFromString(coinType: String): CryptoCurrency? {
        return when (coinType) {
            "Bitcoin Cash" -> BchMain
            "Bitcoin Cash Test" -> BchTest
            "Bitcoin" -> BitcoinMain.get()
            "Bitcoin Test" -> BitcoinTest.get()
            "Mycelium Token" -> MTCoin
            "Mycelium Token Test" -> MTCoinTest
            "Mass Token" -> MASSCoin
            "Mass Token Test" -> MASSCoinTest
            "RMC" -> RMCCoin
            "RMC Test" -> RMCCoinTest
            else -> null
        }
    }

    fun getLabelByAddress(address: GenericAddress): String {
        return getKeyCategoryValueEntry(ADDRESSLABEL_CATEGORY.of(address.toString()), "")
    }

    fun deleteAddressMetadata(address: Address) {
        // delete everything related to this address from metadata
        deleteAllByKey(address.toString())
    }

    fun getAddressByLabel(label: String): Optional<String> {
        val address = getFirstKeyForCategoryValue(ADDRESSLABEL_CATEGORY, label)

        return if (address.isPresent) {
            Optional.of<String>(address.get())
        } else {
            Optional.absent<String>()
        }
    }

    fun storeAddressLabel(address: String, label: String) {
        if (!Strings.isNullOrEmpty(label)) {
            storeKeyCategoryValueEntry(ADDRESSLABEL_CATEGORY.of(address), label)
        }
    }

    fun storeAddressCoinType(address: String, coinType: String) {
        if (coinTypeFromString(coinType) != null) {
            storeKeyCategoryValueEntry(ADDRESSCOINTYPE_CATEGORY.of(address), coinType)
        }
    }

    fun firstMasterseedBackupFinished(): Boolean {
        return masterSeedBackupState == BackupState.VERIFIED
    }

    fun getOtherAccountBackupState(accountId: UUID): BackupState {
        return BackupState.fromString(
                getKeyCategoryValueEntry(OTHER_ACCOUNT_BACKUPSTATE.of(accountId.toString()), BackupState.UNKNOWN.toString())
        )
    }

    fun setOtherAccountBackupState(accountId: UUID, state: BackupState) {
        storeKeyCategoryValueEntry(OTHER_ACCOUNT_BACKUPSTATE.of(accountId.toString()), state.toString())
    }

    fun deleteOtherAccountBackupState(accountId: UUID) {
        deleteByKeyCategory(OTHER_ACCOUNT_BACKUPSTATE.of(accountId.toString()))
    }

    fun isPairedService(serviceName: String): Boolean {
        return java.lang.Boolean.valueOf(getKeyCategoryValueEntry(PAIRED_SERVICES_CATEGORY.of(serviceName), "false"))
    }

    fun setPairedService(serviceName: String, paired: Boolean) {
        storeKeyCategoryValueEntry(PAIRED_SERVICES_CATEGORY.of(serviceName), java.lang.Boolean.toString(paired))
    }

    fun deleteMasterKeyBackupAgeMs() {
        deleteByKeyCategory(SEED_BACKUPSTATE)
    }

    fun setResetPinStartBlockheight(blockChainHeight: Int) {
        storeKeyCategoryValueEntry(PIN_RESET_BLOCKHEIGHT, (blockChainHeight).toString())
    }

    fun clearResetPinStartBlockheight() {
        deleteByKeyCategory(PIN_RESET_BLOCKHEIGHT)
    }

    fun setLastPinSetBlockheight(blockChainHeight: Int) {
        storeKeyCategoryValueEntry(PIN_BLOCKHEIGHT, (blockChainHeight).toString())
    }

    fun clearLastPinSetBlockheight() {
        deleteByKeyCategory(PIN_BLOCKHEIGHT)
    }

    fun getArchived(uuid: UUID): Boolean {
        return "1" == getKeyCategoryValueEntry(ARCHIVED.of(uuid.toString()), "0")
    }

    fun storeArchived(uuid: UUID, archived: Boolean) {
        storeKeyCategoryValueEntry(ARCHIVED.of(uuid.toString()), if (archived) "1" else "0")
    }

    fun storeCoinapultCurrencies(currencies: String) {
        storeKeyCategoryValueEntry(COINAPULT.of("currencies"), currencies)
    }

    fun storeCoinapultLastFlush(marker: Int) {
        storeKeyCategoryValueEntry(COINAPULT.of("flush"), Integer.toString(marker))
    }

    fun storeCoinapultAddress(address: Address, forCurrency: String) {
        storeKeyCategoryValueEntry(COINAPULT.of("last$forCurrency"), address.toString())
    }

    fun deleteCoinapultAddress(forCurrency: String) {
        deleteByKeyCategory(COINAPULT.of("last$forCurrency"))
    }

    fun getCoinapultAddress(forCurrency: String): Optional<Address> {
        val last = getKeyCategoryValueEntry(COINAPULT.of("last$forCurrency"))
        if (!last.isPresent) {
            return Optional.absent<Address>()
        }
        return Optional.fromNullable<Address>(Address.fromString(last.get()))
    }

    fun storeColuAssetCoinSupply(assetIds: String, value: BigDecimal) {
        storeKeyCategoryValueEntry(COLU.of("coinsupply$assetIds"), value.toPlainString())
    }

    fun getColuAssetCoinSupply(assetIds: String): Optional<BigDecimal> {
        val valueEntry = getKeyCategoryValueEntry(COLU.of("coinsupply$assetIds"))
        var result = Optional.absent<BigDecimal>()
        if (valueEntry.isPresent) {
            result = Optional.of<BigDecimal>(BigDecimal(valueEntry.get()))
        }
        return result
    }

    fun storeColuAssetIds(assetIds: String) {
        storeKeyCategoryValueEntry(COLU.of("assetIds"), assetIds)
    }

    fun storeColuBalance(coluAccountUuid: UUID, balance: String) {
        storeKeyCategoryValueEntry(COLU.of("balance$coluAccountUuid"), balance)
    }

    fun getColuBalance(coluAccountUuid: UUID): Optional<String> {
        return getKeyCategoryValueEntry(COLU.of("balance$coluAccountUuid"))
    }


    //Example: currency = "BTC", basecurrency = "USD", market = "Bitstamp", rateValue = "4500.2"
    fun storeExchangeRate(currency: String, baseCurrency: String, market: String, rateValue: String) {
        storeKeyCategoryValueEntry(EXCHANGE_RATES_CATEGORY.of(market + "_" + currency + "_" + baseCurrency), rateValue)
    }

    fun getExchangeRate(currency: String, baseCurrency: String, market: String): Optional<String> {
        return getKeyCategoryValueEntry(EXCHANGE_RATES_CATEGORY.of(market + "_" + currency + "_" + baseCurrency))
    }

    fun addColuAssetUUIDs(assetId: String, uuid: UUID) {
        var value: String

        val uuids = getColuAssetUUIDs(assetId)
        if (uuids.isNotEmpty()) {
            value = TextUtils.join(",", uuids)
            value += ",$uuid"
        } else {
            value = uuid.toString()
        }
        storeKeyCategoryValueEntry(COLU.of(assetId), value)
    }

    fun removeColuAssetUUIDs(assetId: String, uuid: UUID) {

        val uuids = getColuAssetUUIDs(assetId)
        val shortenedList = ArrayList<UUID?>()

        for (curUUID in uuids) {
            if (curUUID == uuid)
                continue
            shortenedList.add(curUUID)
        }

        storeKeyCategoryValueEntry(COLU.of(assetId), TextUtils.join(",", shortenedList))
    }

    fun getColuAssetUUIDs(assetId: String): Array<UUID?> {
        val uuid = getKeyCategoryValueEntry(COLU.of(assetId))

        if (!uuid.isPresent || uuid.get().isEmpty()) {
            return arrayOf()
        }

        val strUuids = uuid.get().split((",").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val uuids = arrayOfNulls<UUID>(strUuids.size)

        for (i in strUuids.indices) {
            uuids[i] = UUID.fromString(strUuids[i])
        }

        return uuids
    }

    fun setLastFullSync(date: Long) {
        storeKeyCategoryValueEntry(SYNC_LAST_FULLSYNC, java.lang.Long.toString(date))
    }

    enum class BackupState(private val _index: Int) {
        UNKNOWN(0), VERIFIED(1), IGNORED(2), NOT_VERIFIED(3);

        override fun toString(): String {
            return Integer.toString(_index)
        }

        fun toInt(): Int {
            return _index
        }

        companion object {
            fun fromString(state: String): BackupState {
                return fromInt(Integer.parseInt(state))
            }

            @JvmStatic
            fun fromInt(integer: Int): BackupState {
                return when (integer) {
                    0 -> UNKNOWN
                    1 -> VERIFIED
                    2 -> IGNORED
                    3 -> NOT_VERIFIED
                    else -> UNKNOWN
                }
            }
        }
    }

    companion object {
        private val COINAPULT = MetadataCategory("coinapult_adddr")
        private val ADDRESSLABEL_CATEGORY = MetadataCategory("addresslabel")
        private val ADDRESSCOINTYPE_CATEGORY = MetadataCategory("addresscointype")
        private val ACCOUNTLABEL_CATEGORY = MetadataCategory("al")
        private val ARCHIVED = MetadataCategory("archived")
        private val TRANSACTION_LABEL_CATEGORY = MetadataCategory("tl")
        private val OTHER_ACCOUNT_BACKUPSTATE = MetadataCategory("single_key_bs")
        private val PAIRED_SERVICES_CATEGORY = MetadataCategory("paired_services")

        private val EXCHANGE_RATES_CATEGORY = MetadataCategory("exchange_rates")

        // various key value fields info for colu
        private val COLU = MetadataCategory("colu_data")
        // associates asset label for each assetId
        private val COLU_ASSET_LABEL_CATEGORY = MetadataCategory("colu_asset_labels")
        // associates all asset data for each assetId
        private val COLU_ASSET_DATA_CATEGORY = MetadataCategory("colu_asset_data")

        private val SEED_BACKUPSTATE = MetadataKeyCategory("seed", "backupstate")
        private val PIN_RESET_BLOCKHEIGHT = MetadataKeyCategory("pin", "reset_blockheight")
        private val PIN_BLOCKHEIGHT = MetadataKeyCategory("pin", "blockheight")
        private val SYNC_LAST_FULLSYNC = MetadataKeyCategory("lastFull", "sync")
        private val SHOW_BIP44_PATH = MetadataKeyCategory("ui", "show_bip44_path")
        private val SWISH_CREDIT_CARD_IS_ENABLED = MetadataKeyCategory("swish_cc", "enable")
        private val SIMPLEX_IS_ENABLED = MetadataKeyCategory("simplex", "enable")
        private val CHANGELLY_IS_ENABLED = MetadataKeyCategory("changelly", "enable")
        private val EMAIL = "email"
        val PAIRED_SERVICE_COINAPULT = "coinapult"
        @JvmField
        val PAIRED_SERVICE_COLU = "colu"
    }
}
