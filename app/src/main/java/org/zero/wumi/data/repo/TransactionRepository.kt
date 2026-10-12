package org.zero.wumi.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.dao.MonthSums
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.dao.TransactionDao
import org.zero.wumi.data.local.dao.TxWithRefs
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.data.local.entity.TransactionEntity
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.di.DeviceId
import org.zero.wumi.util.uuid7
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易仓库。核心保证（spec §6.3）：业务表写入与 outbox 入队始终在
 * 同一 Room 事务内——两者要么同时成功，要么同时回滚。
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val db: WumiDatabase,
    private val txDao: TransactionDao,
    private val outboxDao: OutboxDao,
    @DeviceId private val deviceId: String,
) {
    fun observeRange(from: Long, to: Long): Flow<List<TxWithRefs>> = txDao.observeRange(from, to)

    fun observeMonthSums(from: Long, to: Long): Flow<MonthSums> = txDao.sumsInRange(from, to)

    suspend fun addExpense(
        amountMinor: Long,
        currency: String,
        baseAmountMinor: Long,
        accountId: String,
        categoryId: String?,
        date: Long,
        note: String,
    ): String = insert(TxType.EXPENSE, amountMinor, currency, null, baseAmountMinor, accountId, null, categoryId, date, note)

    suspend fun addIncome(
        amountMinor: Long,
        currency: String,
        baseAmountMinor: Long,
        accountId: String,
        categoryId: String?,
        date: Long,
        note: String,
    ): String = insert(TxType.INCOME, amountMinor, currency, null, baseAmountMinor, accountId, null, categoryId, date, note)

    suspend fun addTransfer(
        amountMinor: Long,
        currency: String,
        baseAmountMinor: Long,
        accountId: String,
        targetAccountId: String,
        date: Long,
        note: String,
    ): String = insert(TxType.TRANSFER, amountMinor, currency, null, baseAmountMinor, accountId, targetAccountId, null, date, note)

    /** 软删除（写墓碑）并入队 outbox，同一事务。 */
    suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            txDao.softDelete(id, now, deviceId)
            outboxDao.enqueue(OutboxEntity(entityType = "transaction", recordId = id, queuedAt = now))
        }
    }

    /** 三类交易统一入口：生成 UUIDv7、同事务写交易表与 outbox，返回新交易 id。 */
    private suspend fun insert(
        type: Int,
        amountMinor: Long,
        currency: String,
        exchangeRate: String?,
        baseAmountMinor: Long,
        accountId: String,
        targetAccountId: String?,
        categoryId: String?,
        date: Long,
        note: String,
    ): String {
        val now = System.currentTimeMillis()
        val tx = TransactionEntity(
            id = uuid7(now),
            type = type,
            amountMinor = amountMinor,
            currency = currency,
            exchangeRate = exchangeRate,
            baseAmountMinor = baseAmountMinor,
            accountId = accountId,
            targetAccountId = targetAccountId,
            categoryId = categoryId,
            date = date,
            note = note,
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
        )
        db.withTransaction {
            txDao.insert(tx)
            outboxDao.enqueue(OutboxEntity(entityType = "transaction", recordId = tx.id, queuedAt = now))
        }
        return tx.id
    }
}
