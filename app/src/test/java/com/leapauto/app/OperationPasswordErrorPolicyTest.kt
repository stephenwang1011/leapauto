package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationPasswordErrorPolicyTest {

    @Test
    fun `isPasswordError returns true for Chinese password error messages`() {
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("控车失败(100002): 操作密码错误")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("密码不正确，请重新输入")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("操作密码校验失败")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("密码错误")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("支付/操作密码已失效，请重试")))
    }

    @Test
    fun `isPasswordError returns true for English or protocol oppwd error messages`() {
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("oppwd check failed")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("Invalid password provided")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("Wrong password")))
        assertTrue(OperationPasswordErrorPolicy.isPasswordError(Exception("Password error")))
    }

    @Test
    fun `isPasswordError returns false for non-password errors`() {
        assertFalse(OperationPasswordErrorPolicy.isPasswordError(Exception("网络超时，请稍后重试")))
        assertFalse(OperationPasswordErrorPolicy.isPasswordError(Exception("未登录")))
        assertFalse(OperationPasswordErrorPolicy.isPasswordError(Exception("车辆已离线")))
        assertFalse(OperationPasswordErrorPolicy.isPasswordError(Exception("系统繁忙，请稍后重试(500)")))
        assertFalse(OperationPasswordErrorPolicy.isPasswordError(null))
    }
}
