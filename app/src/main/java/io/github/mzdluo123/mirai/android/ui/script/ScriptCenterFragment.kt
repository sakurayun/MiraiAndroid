package io.github.mzdluo123.mirai.android.ui.script

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.ooooonly.giteeman.GiteeFile
import io.github.mzdluo123.mirai.android.R
import io.github.mzdluo123.mirai.android.script.ScriptHostFactory
import io.github.mzdluo123.mirai.android.service.ServiceConnector
import kotlinx.android.synthetic.main.fragment_script_center.*
import kotlinx.coroutines.*
import org.jetbrains.anko.*

@ExperimentalStdlibApi
class ScriptCenterFragment : Fragment(), CoroutineScope by MainScope() {

    private lateinit var scriptViewModel: ScriptCenterViewModel
    private lateinit var adapter: ScriptCenterListAdapter
    private lateinit var botServiceConnection: ServiceConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        botServiceConnection = ServiceConnector(requireContext())
        lifecycle.addObserver(botServiceConnection)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_script_center, container, false).also {
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_script_center, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> false
        R.id.action_upload_script -> {
            context?.alert(
                """
                你需要
                1.注册gitee账号
                2.向脚本仓库地址“https://gitee.com/ooooonly/lua-mirai-project/tree/master/ScriptCenter”提交你的“Pull Request”
                3.等待审核即可上架
            """.trimIndent(), "如何上传脚本"
            ) {
                positiveButton("前往仓库地址") {
                    context?.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://gitee.com/ooooonly/lua-mirai-project/tree/master/ScriptCenter")
                        )
                    )
                }
                negativeButton("取消") {}
            }?.show()
            true
        }
        else -> true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        adapter = ScriptCenterListAdapter { selectedFile ->
            if (selectedFile.isFile) {
                var alertDialog: AlertDialog? = null
                alertDialog = context?.alert("是否导入${selectedFile.fileName}？") {
                    yesButton {

                        val progressDialog =
                            context?.indeterminateProgressDialog("正在导入").also { it?.show() }

                        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                            progressDialog?.dismiss()
                            context?.toast("导入失败！\n$throwable")
                        }
                        launch(exceptionHandler) {
                            alertDialog?.dismiss()

                            withContext(Dispatchers.IO) {
                                val filePath =
                                    requireContext().getExternalFilesDir("scripts")!!.absolutePath + "/" + selectedFile.fileName
                                selectedFile.saveToFile(filePath)
                                val scriptType =
                                    ScriptHostFactory.getTypeFromSuffix(filePath.split(".").last())
                                val result = botServiceConnection.botService.createScript(
                                    filePath,
                                    scriptType
                                )
                                if (!result) throw Exception()
                            }
                            progressDialog?.dismiss()
                            context?.toast("导入成功！")
                        }

                    }
                    noButton { }
                }?.build()

                alertDialog?.show()
            } else {
                scriptViewModel.showFiles(selectedFile)
            }
        }
        adapter.setEmptyView(layoutInflater.inflate(R.layout.fragment_script_center_empty, null))
        rcl_scripts.adapter = adapter
        rcl_scripts.layoutManager = LinearLayoutManager(activity)
        /*
        rcl_scripts.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.HORIZONTAL
            )
        )*/

    }

    override fun onResume() {
        super.onResume()
        botServiceConnection.connectStatus.observe(this, Observer {
            if (it) {
                scriptViewModel = ScriptCenterViewModel()
                scriptViewModel.fileList.observe(viewLifecycleOwner, Observer {
                    adapter.data = it.toMutableList()
                    adapter.notifyDataSetChanged()
                })
                scriptViewModel.showFiles(
                    GiteeFile(
                        "ooooonly",
                        "lua-mirai-project",
                        "ScriptCenter",
                        rootLevel = 2,
                        showParent = true
                    )
                )
            }
        })
    }

}