package com.ivy.data.repository.impl

import com.ivy.data.DataWriteEvent
import com.ivy.data.DataWriteEventBus
import com.ivy.data.DeleteOperation
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.write.WriteCategoryDao
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.mapper.CategoryMapper
import com.ivy.testing.TestDispatchersProvider
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.time.Instant
import java.util.UUID

class CategoryRepositoryImplTest : FreeSpec({
    val categoryDao = mockk<CategoryDao>()
    val writeCategoryDao = mockk<WriteCategoryDao>()
    val writeEventBus = mockk<DataWriteEventBus>(relaxed = true)

    fun newRepository(): CategoryRepository = CategoryRepositoryImpl(
        mapper = CategoryMapper(),
        categoryDao = categoryDao,
        writeCategoryDao = writeCategoryDao,
        dispatchersProvider = TestDispatchersProvider,
        writeEventBus = writeEventBus
    )

    "find all not deleted" - {
        "empty list" {
            // given
            val repository = newRepository()
            coEvery { categoryDao.findAll(false) } returns emptyList()

            // when
            val res = repository.findAll(false)

            // then
            res shouldBe emptyList()
        }

        "valid and invalid categories" {
            // given
            val repository = newRepository()
            val id1 = UUID.randomUUID()
            val id3 = UUID.randomUUID()
            coEvery { categoryDao.findAll(false) } returns listOf(
                CategoryEntity(
                    name = "Home",
                    color = 42,
                    icon = null,
                    orderNum = 0.0,
                    isSynced = true,
                    isDeleted = false,
                    id = id1
                ),
                CategoryEntity(
                    name = "",
                    color = 42,
                    icon = null,
                    orderNum = 1.0,
                    isSynced = true,
                    isDeleted = false,
                    id = UUID.randomUUID()
                ),
                CategoryEntity(
                    name = "Fun",
                    color = 42,
                    icon = null,
                    orderNum = 2.0,
                    isSynced = true,
                    isDeleted = false,
                    id = id3
                )
            )

            // when
            val res = repository.findAll(false)

            // then
            res shouldBe listOf(
                Category(
                    name = NotBlankTrimmedString("Home"),
                    color = ColorInt(42),
                    icon = null,
                    orderNum = 0.0,
                    removed = false,
                    lastUpdated = Instant.EPOCH,
                    id = CategoryId(id1)
                ),
                Category(
                    name = NotBlankTrimmedString("Fun"),
                    color = ColorInt(42),
                    icon = null,
                    orderNum = 2.0,
                    removed = false,
                    lastUpdated = Instant.EPOCH,
                    id = CategoryId(id3)
                )
            )
        }
    }

    "find by id" - {
        "null CategoryEntity" {
            // given
            val repository = newRepository()
            val id = UUID.randomUUID()
            coEvery { categoryDao.findById(id) } returns null

            // when
            val category = repository.findById(CategoryId(id))

            // then
            category shouldBe null
        }

        "valid CategoryEntity" {
            // given
            val repository = newRepository()
            val id = UUID.randomUUID()
            coEvery { categoryDao.findById(id) } returns CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 0.0,
                isSynced = true,
                isDeleted = false,
                id = id
            )

            // when
            val category = repository.findById(CategoryId(id))

            // then
            category shouldBe Category(
                name = NotBlankTrimmedString("Home"),
                color = ColorInt(42),
                icon = null,
                orderNum = 0.0,
                removed = false,
                lastUpdated = Instant.EPOCH,
                id = CategoryId(id)
            )
        }

        "invalid CategoryEntity" {
            // given
            val repository = newRepository()
            val id = UUID.randomUUID()
            coEvery { categoryDao.findById(id) } returns CategoryEntity(
                name = "",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isSynced = true,
                isDeleted = false,
                id = UUID.randomUUID()
            )

            // when
            val category = repository.findById(CategoryId(id))

            // then
            category shouldBe null
        }
    }

    "find max order num" - {
        "null from the source" {
            // given
            val repository = newRepository()
            coEvery { categoryDao.findMaxOrderNum() } returns null

            // when
            val num = repository.findMaxOrderNum()

            // then
            num shouldBe 0.0
        }

        "number from the source" {
            // given
            val repository = newRepository()
            coEvery { categoryDao.findMaxOrderNum() } returns 15.0

            // when
            val num = repository.findMaxOrderNum()

            // then
            num shouldBe 15.0
        }
    }

    "save" {
        // given
        val repository = newRepository()
        val id = UUID.randomUUID()
        val category = Category(
            name = NotBlankTrimmedString("Home"),
            color = ColorInt(42),
            icon = null,
            orderNum = 3.0,
            removed = false,
            lastUpdated = Instant.EPOCH,
            id = CategoryId(id)
        )
        coEvery { writeCategoryDao.save(any()) } just runs

        // when
        repository.save(category)

        // then
        coVerify(exactly = 1) {
            writeCategoryDao.save(
                CategoryEntity(
                    name = "Home",
                    color = 42,
                    icon = null,
                    orderNum = 3.0,
                    isSynced = true,
                    isDeleted = false,
                    id = id
                )
            )
        }
        coVerify(exactly = 1) {
            writeEventBus.post(DataWriteEvent.SaveCategories(listOf(category)))
        }
    }

    "save many" {
        // given
        val repository = newRepository()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val categories = listOf(
            Category(
                name = NotBlankTrimmedString("Home"),
                color = ColorInt(42),
                icon = null,
                orderNum = 3.0,
                removed = false,
                lastUpdated = Instant.EPOCH,
                id = CategoryId(id1)
            ),
            Category(
                name = NotBlankTrimmedString("Fun"),
                color = ColorInt(42),
                icon = null,
                orderNum = 4.0,
                removed = false,
                lastUpdated = Instant.EPOCH,
                id = CategoryId(id2)
            ),
            Category(
                name = NotBlankTrimmedString("Health"),
                color = ColorInt(42),
                icon = null,
                orderNum = 5.0,
                removed = false,
                lastUpdated = Instant.EPOCH,
                id = CategoryId(id3)
            )
        )
        coEvery { writeCategoryDao.saveMany(any()) } just runs

        // when
        repository.saveMany(categories)

        // then
        coVerify(exactly = 1) {
            writeCategoryDao.saveMany(
                listOf(
                    CategoryEntity(
                        name = "Home",
                        color = 42,
                        icon = null,
                        orderNum = 3.0,
                        isSynced = true,
                        isDeleted = false,
                        id = id1
                    ),

                    CategoryEntity(
                        name = "Fun",
                        color = 42,
                        icon = null,
                        orderNum = 4.0,
                        isSynced = true,
                        isDeleted = false,
                        id = id2
                    ),

                    CategoryEntity(
                        name = "Health",
                        color = 42,
                        icon = null,
                        orderNum = 5.0,
                        isSynced = true,
                        isDeleted = false,
                        id = id3
                    )
                )
            )
        }
        coVerify(exactly = 1) {
            writeEventBus.post(DataWriteEvent.SaveCategories(categories))
        }
    }

    "delete by id" {
        // given
        val repository = newRepository()
        val categoryId = CategoryId(UUID.randomUUID())
        coEvery { writeCategoryDao.deleteById(any()) } just runs

        // when
        repository.deleteById(categoryId)

        // then
        coVerify(exactly = 1) {
            writeCategoryDao.deleteById(categoryId.value)
        }
        coVerify(exactly = 1) {
            writeEventBus.post(
                DataWriteEvent.DeleteCategories(
                    DeleteOperation.Just(listOf(categoryId))
                )
            )
        }
    }

    "delete all" {
        // given
        val repository = newRepository()
        coEvery { writeCategoryDao.deleteAll() } just runs

        // when
        repository.deleteAll()

        // then
        coVerify(exactly = 1) {
            writeCategoryDao.deleteAll()
        }
        coVerify(exactly = 1) {
            writeEventBus.post(DataWriteEvent.DeleteCategories(DeleteOperation.All))
        }
    }
})
