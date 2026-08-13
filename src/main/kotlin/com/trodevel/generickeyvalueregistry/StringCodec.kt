package com.trodevel.generickeyvalueregistry

object StringCodec {
    fun encode(s: String): String {
        val res = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> res.append("\\\\")
                '\n' -> res.append("\\n")
                '+' -> res.append("++")
                ' ' -> res.append("+")
                else -> res.append(c)
            }
        }
        return res.toString()
    }

    fun decode(s: String): String {
        val res = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') {
                if (i + 1 < s.length) {
                    when (s[i + 1]) {
                        '\\' -> {
                            res.append('\\')
                            i += 2
                        }
                        'n' -> {
                            res.append('\n')
                            i += 2
                        }
                        else -> {
                            res.append(s[i])
                            i += 1
                        }
                    }
                } else {
                    res.append(s[i])
                    i += 1
                }
            } else if (s[i] == '+') {
                if (i + 1 < s.length && s[i + 1] == '+') {
                    res.append('+')
                    i += 2
                } else {
                    res.append(' ')
                    i += 1
                }
            } else {
                res.append(s[i])
                i += 1
            }
        }
        return res.toString()
    }
}
