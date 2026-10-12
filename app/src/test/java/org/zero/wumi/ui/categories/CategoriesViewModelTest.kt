package org.zero.wumi.ui.categories

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.data.repo.CategoryRepository

/** 分类管理 ViewModel 测试：不挂 WumiDbCallback（in-memory 无默认种子），自建分类验证分组与归档。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoriesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: WumiDatabase
    private lateinit var vm: CategoriesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        vm = CategoriesViewModel(CategoryRepository(db, "dev-test"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun save_appears_in_correct_group() = runTest(dispatcher) {
        vm.save(null, null, CategoryType.INCOME, "副业", "💼", 99)
        advanceUntilIdle()
        // Room Flow 在自身线程池重查询，advanceUntilIdle 推不动后台线程——挂起等 state 真正发出数据
        val state = vm.state.first { s -> s.income.any { it.name == "副业" } }
        assertTrue(state.income.any { it.name == "副业" })
        assertTrue(state.expense.none { it.name == "副业" })
    }

    @Test
    fun archived_category_leaves_list() = runTest(dispatcher) {
        vm.save(null, null, CategoryType.EXPENSE, "测试分类", "🧪", 99)
        advanceUntilIdle()
        val id = vm.state.first { s -> s.expense.any { it.name == "测试分类" } }
            .expense.first { it.name == "测试分类" }.id
        vm.archive(id)
        advanceUntilIdle()
        val state = vm.state.first { s -> s.expense.none { it.id == id } }
        assertTrue(state.expense.none { it.id == id })
    }
}
