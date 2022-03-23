package com.example.sp1app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.densowave.scannersdk.Common.CommException
import com.densowave.scannersdk.Common.CommManager
import com.densowave.scannersdk.Common.CommScanner
import com.densowave.scannersdk.Common.CommStatusChangedEvent
import com.densowave.scannersdk.Const.CommConst
import com.densowave.scannersdk.Dto.CommScannerParams
import com.densowave.scannersdk.Dto.RFIDScannerSettings
import com.densowave.scannersdk.Listener.RFIDDataDelegate
import com.densowave.scannersdk.Listener.ScannerAcceptStatusListener
import com.densowave.scannersdk.Listener.ScannerStatusListener
import com.densowave.scannersdk.RFID.RFIDData
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent
import com.densowave.scannersdk.RFID.RFIDException
import com.densowave.scannersdk.RFID.RFIDScanner
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), ScannerAcceptStatusListener, ScannerStatusListener, RFIDDataDelegate {

    // NOTE: The method used in this demo to connect SP1 is not an ideal way to talk to SP1.
    // This demo is just to show when leaving the activity, we have to release the resources, once come back, restart all connection again.

    // For more prefer and standard way, please refer to SP1 SDK Example Source Code.


    var count = 0
    var data = ""

    private var sp1ComScanner: CommScanner? = null
    private var rfidScanner: RFIDScanner? = null
    private var originalRfidSettings: RFIDScannerSettings? = null
    // To control resume.
    private var resumed: Boolean = false

    private var sp1Connected = false
    var TAG = "SP1_App"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRead.setOnClickListener {
            StartReading()
        }

        btnStop.setOnClickListener {
            StopReading()
        }

        btnNext.setOnClickListener {
            val intent = Intent(this,NextActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "-----------> PAUSE")

        disConnectSP1()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "-----------> RESUME")

        linkSp1()
    }

    override fun onDestroy() {
        Log.d(TAG, "-----------> ON DESTROY")
        try {
            disConnectSP1()

        }catch(e:Exception){
            Log.e(TAG, "Error: " + e.message)
        }
        super.onDestroy()
    }

    private fun linkSp1(){
        // start waiting for SP1 to connect
        CommManager.addAcceptStatusListener(this)
        CommManager.startAccept()
    }

    fun StartReading(){
        btnRead.isEnabled = false
        btnStop.isEnabled = true

        try {
            rfidScanner!!.close()
        } catch (e: RFIDException) {
            Log.e(TAG, "Fail to close SP1. Error: " + e.message)
        }

        try {
            rfidScanner!!.openInventory()
        } catch (e: RFIDException) {
            Log.e(TAG, "Fail to start SP1 inventory. Error: " + e.message)
        }
    }

    fun StopReading(){
        btnStop.isEnabled = false
        btnRead.isEnabled = true

        try {
            rfidScanner!!.close()
        } catch (e: RFIDException) {
            Log.e(TAG, "Fail to close SP1. Error: " + e.message)
        }
    }

    private fun byteToString(bytes: ByteArray): String? {
        val stringBuilder = StringBuilder()
        for(b in bytes){
            stringBuilder.append(String.format("%02X", b))
        }
        return stringBuilder.toString()
    }

    override fun onRFIDDataReceived(p0: CommScanner?, rfidDataReceivedEvent: RFIDDataReceivedEvent?) {
        Log.d(TAG, "OnRFIDDataReceived")
        count++

        val stringBuilder = StringBuilder()
        stringBuilder.append("count$count\n")
        val rfidDataList: List<RFIDData> = rfidDataReceivedEvent!!.rfidData

        for (rfidData in rfidDataList) {
            stringBuilder.append("""data:${byteToString(rfidData.data)}""".trimIndent())
            stringBuilder.append("""PC:${rfidData.pc}""".trimIndent())
            stringBuilder.append("""Polarization:${rfidData.polarization}""".trimIndent())
            stringBuilder.append("""RSSI:${rfidData.rssi}""".trimIndent())
            stringBuilder.append("""UII:${byteToString(rfidData.uii)}""".trimIndent())
        }

        data = stringBuilder.toString()
        Log.d(TAG, "data = $data")

        try {
            runOnUiThread {
                etData.setText("Read Count: $count.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fail to communicate with SP1. Error: " + e.message)
        }
    }

    override fun OnScannerAppeared(scanner: CommScanner?) {
        if(scanner != null){
            try {
                sp1ComScanner = scanner
                sp1ComScanner?.claim()
            } catch (e: CommException) {
                Log.e(TAG, "Error: " + e.message)
            }

            CommManager.endAccept()
            CommManager.removeAcceptStatusListener(this)

            // get RFID scanner object
            rfidScanner = sp1ComScanner?.getRFIDScanner()
            rfidScanner!!.setDataDelegate(this)

            // configure SP1 settings
            try {
                originalRfidSettings = rfidScanner!!.settings
                val myScannerSettings = rfidScanner!!.settings
                myScannerSettings.scan.triggerMode = RFIDScannerSettings.Scan.TriggerMode.CONTINUOUS1
                myScannerSettings.scan.powerLevelRead = 30
                myScannerSettings.scan.powerLevelWrite = 30
                rfidScanner!!.settings = myScannerSettings
            } catch (e: RFIDException) {
                Log.e(TAG, "Error: " + e.message)
            }

            try {
                runOnUiThread {
                    etData.setText("Connected to " + sp1ComScanner?.btLocalName + ".")

                    btnRead.isEnabled = true
                    btnStop.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to communicate with SP1. Error: " + e.message)
            }
        }
    }

    override fun onScannerStatusChanged(p0: CommScanner?, state: CommStatusChangedEvent?) {
        val scannerStatus: CommConst.ScannerStatus = state!!.status

        if (scannerStatus == CommConst.ScannerStatus.CLOSE_WAIT) {
            Log.d(TAG, "Close wait")
        }
    }

    private fun disConnectSP1(){
        Log.d(TAG, "-----------> Disconnect")
        try {
            rfidScanner?.close()

            sp1ComScanner?.close()

            CommManager.endAccept()
            CommManager.removeAcceptStatusListener(this)

        }catch(e: RFIDException){
            Log.e(TAG, "Disconnect SP1 Error: " + e.message)
        }


        btnRead.isEnabled =  false
        btnStop.isEnabled = false
    }
}