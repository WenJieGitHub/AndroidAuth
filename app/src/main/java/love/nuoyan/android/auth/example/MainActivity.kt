package love.nuoyan.android.auth.example

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import love.nuoyan.android.auth.Auth
import love.nuoyan.android.auth.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        Auth.withXM().onActivityCreate(this)

        Auth.withWX().registerCallback {
            Log.i("AuthResult", it.toString())
        }
        mBinding.wxLogin.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withWX().login().toString())
            }
        }
        mBinding.wxShareLink.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withWX().shareLink("http://www.baidu.com").toString())
            }
        }
        mBinding.wbLogin.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withWB().login().toString())
            }
        }
        mBinding.wbShareLink.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withWB().shareLink("http://www.baidu.com").toString())
            }
        }
        mBinding.zfbPay.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withZFB().pay("zfb").toString())
            }
        }
        mBinding.ylPay.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withYL().pay("yl").toString())
            }
        }
        mBinding.qqLogin.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withQQ().login().toString())
            }
        }
        mBinding.qqShare.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withQQ().shareLink("http://www.baidu.com", "123").toString())
            }
        }

        mBinding.xmLogin.setOnClickListener {
            lifecycleScope.launch {
                Log.i("AuthResult", Auth.withXM().login(this@MainActivity).toString())
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        Auth.withXM().onActivityDestroy()
    }
}