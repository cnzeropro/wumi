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
import org.zero.wumi.util.FxConverter
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

    /**
     * 记支出：baseAmountMinor 由 [fxRate] 当笔汇率快照换算（FxConverter 唯一出口）并随笔记入
     * exchangeRate（本位币传 "1"）；与 outbox 入队同事务。
     */
    suspend fun addExpense(
        amountMinor: Long,
        currency: String,
        accountId: String,
        categoryId: String?,
        date: Long,
        note: String,
        fxRate: String = "1",
    ): String = insert(
        TxType.EXPENSE, amountMinor, currency, fxRate,
        FxConverter.toBaseMinor(amountMinor, fxRate),
        accountId, null, categoryId, date, note,
    )

    /** 记收入：换算与快照语义同 [addExpense]。 */
    suspend fun addIncome(
        amountMinor: Long,
        currency: String,
        accountId: String,
        categoryId: String?,
        date: Long,
        note: String,
        fxRate: String = "1",
    ): String = insert(
        TxType.INCOME, amountMinor, currency, fxRate,
        FxConverter.toBaseMinor(amountMinor, fxRate),
        accountId, null, categoryId, date, note,
    )

    suspend fun addTransfer(
        amountMinor: Long,
        currency: String,
        baseAmountMinor: Long,
        accountId: String,
        targetAccountId: String,
        date: Long,
        note: String,
    ): String = insert(TxType.TRANSFER, amountMinor, currency, null, baseAmountMinor, accountId, targetAccountId, null, date, note)

    /** 取单笔交易及其引用；已软删视为不存在（编辑页不可再打开）。 */
    suspend fun getWithRefs(id: String): TxWithRefs? {
        val ref = txDao.getByIdWithRefs(id) ?: return null
        return if (ref.tx.deletedAt != null) null else ref
    }

    /**
     * 编辑保存（LWW 语义）：upsert 覆盖原行——保持原 id 与 createdAt，
     * 只 bump updatedAt/deviceId；并入队 outbox，同一事务。原交易不存在则静默忽略。
     */
    suspend fun update(
        id: String,
        type: Int,
        amountMinor: Long,
        currency: String,
        baseAmountMinor: Long,
        accountId: String,
        targetAccountId: String?,
        categoryId: String?,
        date: Long,
        note: String,
    ) {
        val now = System.currentTimeMillis()
        val old = txDao.getById(id) ?: return
        db.withTransaction {
            txDao.insert(
                old.copy(
                    type = type, amountMinor = amountMinor, currency = currency, baseAmountMinor = baseAmountMinor,
                    accountId = accountId, targetAccountId = targetAccountId, categoryId = categoryId,
                    date = date, note = note, updatedAt = now, deviceId = deviceId,
                ),
            )
            outboxDao.enqueue(OutboxEntity(entityType = "transaction", recordId = id, queuedAt = now))
        }
    }

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
