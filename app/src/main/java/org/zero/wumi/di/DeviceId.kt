package org.zero.wumi.di

import javax.inject.Qualifier

/** 标注设备唯一标识（首启生成 UUIDv4 并持久化到 settings 表，spec §5.1）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceId
