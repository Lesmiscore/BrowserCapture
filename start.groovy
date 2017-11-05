@Grab('com.github.nao20010128nao:BrowserCapture:2ef87c7d')
@GrabResolver(name='jitpack',root='https://jitpack.io/')
import com.nao20010128nao.BrowserCapture.MainKt

def args=[]

if(System.getProperty('os.name').toLowerCase().contains('linux')&&System.getenv('PATH')){
    // X server should be provided by Xvfb.
    args<<'--headless=false'
}

MainKt.main(args as String[])
