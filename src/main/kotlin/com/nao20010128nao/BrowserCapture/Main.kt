package com.nao20010128nao.BrowserCapture

import com.google.common.io.ByteStreams
import joptsimple.OptionParser
import net.freeutils.httpserver.HTTPServer
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.jsoup.Jsoup
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxBinary
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import java.awt.Toolkit
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO


fun main(args:Array<String>) {
    if(System.getProperty("os.name").toLowerCase(Locale.ENGLISH)!="linux"){
        println("Non-Linux system is not supported.")
        println("System.getProperty(\"os.name\") = ${System.getProperty("os.name")}")
        System.exit(1)
        return
    }
    installWebDriverBinaries()
    val optParam=OptionParser()
    optParam.accepts("port").withRequiredArg().defaultsTo("8080")
    optParam.accepts("headless").withOptionalArg().defaultsTo("true")
    val opt=optParam.parse(*args)
    val server= HTTPServer(opt.valueOf("port").toString().toInt())
    val headless =opt.valueOf("headless").toString().toBoolean()
    server.getVirtualHost(null).also {
        it.addContext("/", TopPage())
        it.addContext("/css", DynamicCss())
        it.addContext("/chrome",Chrome(headless))
        it.addContext("/firefox",Firefox(headless))
    }
    server.start()
    println("Server is ready")
}

fun installWebDriverBinaries(){
    println("WebDriver binary is being detected or installed")
    if(File("./chromedriver").let { it.exists()&&it.canExecute() }){
        /* Check current directory */
        System.setProperty("webdriver.chrome.driver",File("./chromedriver").absolutePath)
    }else if(findInPath("chromedriver",true)!=null){
        /* Check $PATH */
        System.setProperty("webdriver.chrome.driver",findInPath("chromedriver",true)!!.absolutePath)
    }else{
        /* Download into tmp directory */
        URL("http://chromedriver.storage.googleapis.com/2.29/chromedriver_linux64.zip").openStream().use {
            ZipInputStream(it).use { tar ->
                while(true){
                    val entry= tar.nextEntry ?: break
                    if(entry.name=="chromedriver"){
                        val file=File.createTempFile("browser","capture")
                        file.outputStream().use {
                            ByteStreams.copy(tar,it)
                        }
                        file.setExecutable(true)
                        file.deleteOnExit()
                        System.setProperty("webdriver.chrome.driver",file.absolutePath)
                    }
                }
            }
        }
    }
    if(File("./geckodriver").let { it.exists()&&it.canExecute() }){
        /* Check current directory */
        System.setProperty("webdriver.gecko.driver",File("./geckodriver").absolutePath)
    }else if(findInPath("geckodriver",true)!=null){
        /* Check $PATH */
        System.setProperty("webdriver.gecko.driver",findInPath("geckodriver",true)!!.absolutePath)
    }else{
        /* Download into tmp directory */
        GZIPInputStream(URL("https://github.com/mozilla/geckodriver/releases/download/v0.19.1/geckodriver-v0.19.1-linux64.tar.gz").openStream()).use {
            TarArchiveInputStream(it).use { tar ->
                while(true){
                    val entry= tar.nextTarEntry ?: break
                    if(entry.name=="geckodriver"){
                        val file=File.createTempFile("browser","capture")
                        file.outputStream().use {
                            ByteStreams.copy(tar,it)
                        }
                        file.setExecutable(true)
                        file.deleteOnExit()
                        System.setProperty("webdriver.gecko.driver",file.absolutePath)
                    }
                }
            }
        }
    }
    println("Set \"webdriver.chrome.driver\" to ${System.getProperty("webdriver.chrome.driver")}")
    println("Set \"webdriver.gecko.driver\" to ${System.getProperty("webdriver.gecko.driver")}")
}

class TopPage : ContextHandler{
    override fun onRequest(req: HTTPServer.Request, resp: HTTPServer.Response): Int {
        resp.sendHeaders(200)
        val doc=Jsoup.parse(openHtml("top"),"utf-8","")
        val content=parseRequestedImage(req)
        if(content==null){
            doc.select(".chrome").attr("src","/chrome")
            doc.select(".firefox").attr("src","/firefox")
        }else{
            doc.select(".chrome").attr("src","/chrome?${req.params.toQuery()}")
            doc.select(".firefox").attr("src","/firefox?${req.params.toQuery()}")
            val (url,width,height)=content
            doc.head().appendElement("link").also {
                it.attr("rel","stylesheet")
                it.attr("type","text/css")
                it.attr("href","/css/image-$width-$height.css")
            }
            doc.select("input[name=\"url\"]").attr("value",url)
            doc.select("input[name=\"width\"]").attr("value",width.toString())
            doc.select("input[name=\"height\"]").attr("value",height.toString())
        }
        val stream=doc.html().byteInputStream(StandardCharsets.UTF_8)
        ByteStreams.copy(stream,resp.body)
        return 0
    }
}

class DynamicCss: ContextHandler{
    override fun onRequest(req: HTTPServer.Request, resp: HTTPServer.Response): Int {
        val path=req.path.let {
            when {
                it.startsWith("/css/") -> it.substring(5)
                it.startsWith("/") -> it.substring(1)
                else -> it
            }
        }.let {
            when {
                it.endsWith(".css") -> it.substring(0,it.length-4)
                else -> it
            }
        }
        if(path.startsWith("image-")){
            val (width,height)=path.split("-").drop(1).map { it.toDouble() }
            resp.sendHeaders(200)
            //resp.body.write(".screenshot#wrapper:before { padding-top: ${width*100/height}%; }".utf8Bytes())
            return 0
        }
        resp.sendHeaders(404)
        return 0
    }
}

class Chrome(private val headless:Boolean) : BrowserBase(){
    override fun startBrowser(width:Int,height:Int): WebDriver {
        val options=ChromeOptions()
        if(headless) {
            options.addArguments("--headless")
        }
        return ChromeDriver(options).also {
            it.manage().window().size = Dimension(width, height)
        }
    }

    override val path: String
        get() = "chrome"
}
class Firefox(private val headless:Boolean) : BrowserBase(){
    override fun startBrowser(width:Int,height:Int): WebDriver {
        val options=FirefoxOptions()
        val firefoxBinary = FirefoxBinary()
        if(headless){
            firefoxBinary.addCommandLineOptions("--headless")
        }
        options.binary=firefoxBinary
        return FirefoxDriver(options).also {
            it.manage().window().size = Dimension(width, height)
        }
    }

    /* Use hard-coded value because Firefox's screenshot returns with wrong size */
    override fun margins(): Pair<Int, Int> = 0 to 37

    override val path: String
        get() = "firefox"
}


abstract class BrowserBase: ContextHandler{
    val cache: Screenshots = HashSet()
    val margins:Pair<Int,Int>

    init{
        margins=margins()
        println("Margins for $path: ${margins.first}x${margins.second}")
    }

    override fun onRequest(req: HTTPServer.Request, resp: HTTPServer.Response): Int {
        if(req.params.isEmpty()){
            resp.sendHeaders(200)
            ByteStreams.copy(openHtml("image_init"),resp.body)
            return 0
        }
        val triple= parseRequestedImage(req)
        if(triple==null){
            resp.sendHeaders(404)
            ByteStreams.copy(appError("Failed to parse query"),resp.body)
            return 0
        }
        val (url,width,height)=triple
        if(!url.matches("^https?://.+$".toRegex())){
            resp.sendHeaders(404)
            ByteStreams.copy(appError("Invalid scheme or something else"),resp.body)
            return 0
        }
        if(cache.find(url, width, height)==null){
            /* Queue the request and redirect */
            val ss=Screenshot(url,width,height)
            cache.add(ss)
            take(ss, {startBrowser(width+margins.first, height+margins.second)}, {cache.remove(ss)})
            reopenPage(req, resp)
            return 0
        }
        val image= cache.find(url, width, height)!!.image
        if(image==null){
            /* Redirect for next chance */
            reopenPage(req, resp)
        }else{
            /* Screenshot is taken, so send it */
            val bytes=Base64.getDecoder().decode(image)
            resp.sendHeaders(200,bytes.size.toLong(),0,null,"image/png",null)
            resp.body.write(bytes)
        }
        return 0
    }

    fun reopenPage(req: HTTPServer.Request, resp: HTTPServer.Response) {
        /*Thread.sleep(4000)
        resp.redirect("/$path?${req.params.addRandQuery().toQuery()}",false)*/
        resp.sendHeaders(200)
        val doc=Jsoup.parse(openHtml("image_redirect"),"utf-8","")!!
        doc.head().appendElement("meta").also {
            it.attr("http-equiv", "refresh")
            it.attr("content", "4;url=/$path?${req.params.addRandQuery().toQuery()}")
        }
        //println(doc.html())
        resp.body.write(doc.html().utf8Bytes())
    }
    abstract fun startBrowser(width:Int,height:Int):WebDriver
    abstract val path:String
    open fun margins(): Pair<Int,Int>{
        val (width,height)=800 to 600
        val driver=startBrowser(width,height)
        Thread.sleep(2*1000)
        driver.get("https://google.com/")
        Thread.sleep(5*1000)
        try {
            val ss=(driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
            val image= ImageIO.read(ss.inputStream())!!
            return Pair(width-image.width,height-image.height)
        }finally {
            driver.close()
        }
    }
}

data class Screenshot(val url:String,val width:Int,val height:Int){
    /** Must be base64 encoded */
    var image: String?=null
}

interface ContextHandler:HTTPServer.ContextHandler{
    override fun serve(p0: HTTPServer.Request, p1: HTTPServer.Response): Int {
        println(p0.rawUri)
        try {
            return onRequest(p0,p1)
        }catch (e:Throwable){
            e.printStackTrace()
            throw e
        }
    }
    fun onRequest(req: HTTPServer.Request, resp: HTTPServer.Response): Int
    fun openHtml(name:String):InputStream
            = javaClass.classLoader.getResourceAsStream("browsercapture_$name.html")
    fun appError(title:String):InputStream{
        val doc= Jsoup.parse(openHtml("image_error"),"utf-8","")
        doc.select(".errortext")[0].text(title)
        return doc.html().byteInputStream(StandardCharsets.UTF_8)
    }
}

fun parseRequestedImage(req: HTTPServer.Request):Triple<String,Int,Int>?{
    val params=req.params
    return if (!params.containsKey("url") && !params.containsKey("width") && !params.containsKey("height"))
        null
    else
        Triple(params["url"]!!,params["width"]!!.toInt(),params["height"]!!.toInt())
}

val executor: ExecutorService =Executors.newFixedThreadPool(5)

fun take(ss:Screenshot,driverFactory:()->WebDriver,reject:()->Unit){
    ss.image =null
    executor.submit({
        (0..4).forEach {
            val driver=driverFactory()
            try {
                println("${ss.url}: Opening")
                driver.get(ss.url)
                /* Wait for 20 seconds to render */
                println("${ss.url}: Waiting for render")
                Thread.sleep(1000*20)
                /* Take screenshot */
                println("${ss.url}: Getting image")
                ss.image=(driver as TakesScreenshot).getScreenshotAs(OutputType.BASE64)
                return@submit
            }catch(e: Throwable){
                e.printStackTrace()
            }finally {
                /* Close browser */
                println("${ss.url}: Closing browser")
                driver.close()
            }
        }
        reject()
    })
}
