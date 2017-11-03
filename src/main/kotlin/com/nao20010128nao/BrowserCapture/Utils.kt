package com.nao20010128nao.BrowserCapture

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

typealias Screenshots = MutableSet<Screenshot>

fun Screenshots.find(url:String,width:Int,height:Int):Screenshot? =
        filter { it.url==url }
        .filter { it.width==width }
        .firstOrNull { it.height==height }

fun Map<String,String>.addRandQuery():Map<String,String>{
    val mutable=toMutableMap()
    mutable["rand"]=UUID.randomUUID().toString().replace("-","")
    return mutable
}
fun Map<String,String>.toQuery():String = entries.joinToString("&") {
    "${URLEncoder.encode(it.key,"utf-8")}=${URLEncoder.encode(it.value,"utf-8")}"
}
fun CharSequence.utf8Bytes():ByteArray=
        "$this".toByteArray(StandardCharsets.UTF_8)
