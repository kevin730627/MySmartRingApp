package com.mysmartring.ring

/** 封包格式不符預期。Raw Frame 仍會保存，修正解析器後可由 Rebuild 重新解析 */
class ProtocolException(message: String) : RuntimeException(message)
