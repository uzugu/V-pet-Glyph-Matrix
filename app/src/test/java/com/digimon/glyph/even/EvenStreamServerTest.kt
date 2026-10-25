package com.digimon.glyph.even

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class EvenStreamServerTest {
    private val token="a".repeat(32)
    @Test fun authenticationOrderingAndInputValidation() {
        val presses=mutableListOf<String>()
        val server=EvenStreamServer(token,{"{\"ready\":true}"},{button,_,_->presses.add(button);true},0)
        fun request(path:String,method:String="GET",body:String?=null,auth:Boolean=true):Int {
            val c=URL("http://127.0.0.1:${server.port}/$path").openConnection() as HttpURLConnection
            try {
                c.connectTimeout=2000;c.readTimeout=2000;c.requestMethod=method
                if(auth)c.setRequestProperty("Authorization","Bearer $token")
                if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toByteArray())}}
                return c.responseCode
            } finally {c.disconnect()}
        }
        fun command(seq:Int,button:String="a")="""{"session":"12345678-1234-1234-1234-123456789abc","seq":$seq,"button":"$button","inputEpoch":"12345678-1234-1234-1234-123456789abc:0","romHash":"${"b".repeat(64)}"}"""
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
    @Test fun delayedRequestsCannotCrossSaveReplacementOrPhoneServiceRestart() {
        val inputs = java.util.concurrent.atomic.AtomicReference(com.digimon.glyph.input.InputSession())
        val presses = java.util.concurrent.atomic.AtomicInteger()
        val server = EvenStreamServer(token, { "{}" }, { _, _, epoch ->
            if (inputs.get().acceptsWire(epoch)) { presses.incrementAndGet(); true } else false
        }, 0)
        fun command(seq: Int, epoch: String?): Int {
            val c = URL("http://127.0.0.1:${server.port}/v1/press").openConnection() as HttpURLConnection
            val fields = mutableMapOf<String, Any>("session" to "12345678-1234-1234-1234-123456789abc",
                "seq" to seq, "button" to "a", "romHash" to "b".repeat(64))
            if (epoch != null) fields["inputEpoch"] = epoch
            try {
                c.connectTimeout = 2000; c.readTimeout = 2000; c.requestMethod = "POST"
                c.setRequestProperty("Authorization", "Bearer $token")
                c.doOutput = true
                c.outputStream.use { it.write(com.google.gson.Gson().toJson(fields).toByteArray()) }
                return c.responseCode
            } finally { c.disconnect() }
        }
        try {
            val before = inputs.get().wireEpoch()
            assertEquals(200, command(1, before))
            val replacement = inputs.get().beginReplacement()!!
            assertEquals(409, command(2, before))
            inputs.get().finishReplacement(replacement)
            assertEquals(409, command(3, before))
            val resumed = inputs.get().wireEpoch()
            assertEquals(200, command(4, resumed))
            inputs.set(com.digimon.glyph.input.InputSession())
            assertEquals(409, command(5, resumed))
            assertEquals(200, command(6, inputs.get().wireEpoch()))
            assertEquals(400, command(7, null)) // old clients must update before sending input
            assertEquals(3, presses.get())
        } finally { server.close() }
    }

}
