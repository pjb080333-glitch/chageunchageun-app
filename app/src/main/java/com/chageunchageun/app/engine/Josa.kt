package com.chageunchageun.app.engine

/** 한국어 조사 처리: 마지막 글자 받침 유무로 을/를, 이/가 등을 고른다. 한글이 아니면 받침 없음으로 처리. */
internal fun String.josa(withBatchim: String, withoutBatchim: String): String {
    // "전조등(하향)"처럼 괄호로 끝나면 괄호 안 마지막 글자를 기준으로 본다.
    val last = trimEnd(')', ']', ' ').lastOrNull() ?: return this + withoutBatchim
    if (last !in '가'..'힣') return this + withoutBatchim
    val hasBatchim = (last.code - 0xAC00) % 28 != 0
    return this + if (hasBatchim) withBatchim else withoutBatchim
}

internal fun String.eulReul() = josa("을", "를")
internal fun String.iGa() = josa("이", "가")
