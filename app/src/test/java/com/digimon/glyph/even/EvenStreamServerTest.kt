package com.digimon.glyph.even

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class EvenStreamServerTest {
    private val token="a".repeat(32)
    @Test fun authenticationOrderingAndInputValidation() {
        val presses=mutableListOf<String>()
        val server=EvenStreamServer(token,{"{\"ready\":true}"},{button,_->presses.add(button);true},0)
        fun request(path:String,method:String="GET",body:String?=null,auth:Boolean=true):Int {
            val c=URL("http://127.0.0.1:${server.port}/$path").openConnection() as HttpURLConnection
            try {
                c.connectTimeout=2000;c.readTimeout=2000;c.requestMethod=method
                if(auth)c.setRequestProperty("Authorization","Bearer $token")
                if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toByteArray())}}
                return c.responseCode
            } finally {c.disconnect()}
        }
        fun command(seq:Int,button:String="a")="""{"session":"12345678-1234-1234-1234-123456789abc","seq":$seq,"button":"$button","romHash":"${"b".repeat(64)}"}"""
        try {
            assertEquals(401,request("v1/frame",auth=false))
            assertEquals(200,request("v1/frame"))
            assertEquals(204,request("v1/press","OPTIONS",auth=false))
            assertEquals(200,request("v1/press","POST",command(1)))
            assertEquals(409,request("v1/press","POST",command(1)))
            assertEquals(409,request("v1/press","POST",command(3)))
            assertEquals(400,request("v1/press","POST",command(2,"invalid")))
            assertEquals(200,request("v1/press","POST",command(2,"b")))
            assertEquals(listOf("a","b"),presses)
            assertEquals(400,request("v1/press","POST","x".repeat(1025)))
        } finally {server.close()}
        assertThrows(Exception::class.java){request("v1/frame")}
    }
}
