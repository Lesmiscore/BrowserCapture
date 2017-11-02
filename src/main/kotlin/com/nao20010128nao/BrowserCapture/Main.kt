package com.nao20010128nao.BrowserCapture

import com.google.common.io.ByteStreams
import joptsimple.OptionParser
import net.freeutils.httpserver.HTTPServer
import java.util.*

fun main(args:Array<String>) {
    if(System.getProperty("os.name").toLowerCase(Locale.ENGLISH)!="linux"){
        println("Non-Linux system is not supported.")
        println("System.getProperty(\"os.name\") = ${System.getProperty("os.name")}")
        System.exit(1)
    }
    val optParam=OptionParser()
    optParam.accepts("port").withRequiredArg().defaultsTo("8080")
    val opt=optParam.parse(*args)
    val server= HTTPServer(opt.valueOf("port").toString().toInt())
    server.getVirtualHost(null).also {
        it.addContext("/", TopPage())
    }
    server.start()
    println("Server is ready")
}

class TopPage : ContextHandler(){
    override fun serve(req: HTTPServer.Request, resp: HTTPServer.Response): Int {
        resp.sendHeaders(200)
        ByteStreams.copy(javaClass.classLoader.getResourceAsStream("browsercapture_top.html"),resp.body)
        return 0
    }
}


abstract class ContextHandler:HTTPServer.ContextHandler{
    override abstract fun serve(req: HTTPServer.Request, resp: HTTPServer.Response): Int
}