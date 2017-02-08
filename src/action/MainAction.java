package action;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import codegenerator.ResourceTemplateProvider;
import ui.InputDialog;
import util.Util;

/**
 * Created by gisinfo on 2016/12/26.
 */
public class MainAction extends AnAction implements InputDialog.OnDialogOKListener {

    private static final String CONTRACT_TEMPLATE = "Contract_template";
    private static final String PRESENTER_TEMPLATE = "Presenter_template";
    private static final String BASEPRESENTER_TEMPLATE = "BasePresenter_template";
    private static final String BASEVIEW_TEMPLATE = "BaseView_template";

    private Project mProject;

    /**
     * 包名
     * e.g. com.example.kop.myapplication
     */
    private String mPackage;
    /**
     * 当前文件的包名
     * e.g. com.example.gisinfo.myapplication.bbb
     */
    private String mActivityPackage;
    /**
     * 当前右键Activity的路径
     * e.g. D:/IdeaProjects/MyApplication/app/src/main/java/com/example/gisinfo/myapplication/bbb
     */
    private String mSelectPath;
    /**
     * Base文件路径
     * e.g. D:/IdeaProjects/MyApplication/app/src/main/java/com/example/gisinfo/myapplication
     */
    private String mBasePath;

    @Override
    public void actionPerformed(AnActionEvent e) {
        mProject = getEventProject(e);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);

        mPackage = getPackageName(mProject, e);
        String tempaPackage = mPackage.replace(".", "/");

        PsiFile mFile = PsiUtilBase.getPsiFileInEditor(editor, mProject);
        mSelectPath = mFile.getViewProvider().getVirtualFile().getParent().getPath();

        mBasePath = mSelectPath.substring(0, mSelectPath.lastIndexOf(tempaPackage)) + tempaPackage;

        String tempSelectPath = mSelectPath.replace("/", ".");
        mActivityPackage = tempSelectPath.substring(tempSelectPath.indexOf(mPackage), tempSelectPath.length());

        showInputDialog();
    }

    /**
     * 刷新项目列表
     */
    private void refreshProject() {
        mProject.getBaseDir().refresh(false, true);
    }

    /**
     * 显示对话框
     */
    private void showInputDialog() {
        InputDialog dialog = new InputDialog();
        dialog.setOnDialogOKListener(this);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    /**
     * AndroidManifest文件可能存在的路径
     *
     * @return ArrayList<String>
     */
    private ArrayList<String> possibleManifestPaths() {
        return Lists.newArrayList("", "app/", "app/src/main/", "src/main/", "res/");
    }

    /**
     * 根据路径得到AndroidManifest文件
     *
     * @param project project
     * @param path    path
     * @return VirtualFile
     */
    private VirtualFile getManifestFileFromPath(Project project, String path) {
        VirtualFile folder = project.getBaseDir().findFileByRelativePath(path);
        if (folder != null) {
            return folder.findChild("AndroidManifest.xml");
        }
        return null;
    }

    private ModuleRootManager getModuleRootManager(AnActionEvent event) {
        return ModuleRootManager.getInstance(event.getData(LangDataKeys.MODULE));
    }

    /**
     * AndroidManifest文件可能存在的路径
     *
     * @return List<String>
     */
    private List<String> getSourceRootPathList(Project project, AnActionEvent event) {
        List<String> sourceRoots = Lists.newArrayList();
        String projectPath = org.apache.velocity.util.StringUtils.normalizePath(project.getBasePath());
        for (VirtualFile virtualFile : getModuleRootManager(event).getSourceRoots(false)) {
            sourceRoots.add(org.apache.velocity.util.StringUtils.normalizePath(virtualFile.getPath()).replace(projectPath, ""));
        }
        return sourceRoots;
    }

    /**
     * 得到项目包名
     *
     * @param project project
     * @param event   event
     * @return String
     */
    private String getPackageName(Project project, AnActionEvent event) {
        try {
            for (String path : possibleManifestPaths()) {
                VirtualFile file = getManifestFileFromPath(project, path);
                if (file != null && file.exists()) {
                    return parseManifest(file);
                }
            }
            for (String path : getSourceRootPathList(project, event)) {
                VirtualFile file = getManifestFileFromPath(project, path);
                if (file != null && file.exists()) {
                    return parseManifest(file);
                }
            }
        } catch (Exception ignored) {

        }
        return "";
    }

    /**
     * 解析AndroidManifest文件
     *
     * @param file file
     * @return String
     */
    private String parseManifest(VirtualFile file) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file.getInputStream());

            NodeList dogList = doc.getElementsByTagName("manifest");
            for (int i = 0; i < dogList.getLength(); i++) {
                Node dog = dogList.item(i);
                Element elem = (Element) dog;
                return elem.getAttribute("package");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 创建MVP文件
     */
    private void createMVPFiles(String className, String content) {
        writetoFile(content, mSelectPath, className);
    }

    /**
     * 创建Base文件
     */
    private void createBaseFiles(String className, String content) {
        writetoFile(content, mBasePath + File.separatorChar + "base", className);
    }

    private void writetoFile(String content, String filepath, String filename) {
        try {
            File floder = new File(filepath);
            // if file doesnt exists, then create it
            if (!floder.exists()) {
                floder.mkdirs();
            }
            File file = new File(filepath + "/" + filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(String className) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                ResourceTemplateProvider resourceTemplateProvider = new ResourceTemplateProvider();
                String contract = resourceTemplateProvider.provideTemplateForName(CONTRACT_TEMPLATE);
                String presenter = resourceTemplateProvider.provideTemplateForName(PRESENTER_TEMPLATE);
                String basePresenter = resourceTemplateProvider.provideTemplateForName(BASEPRESENTER_TEMPLATE);
                String baseView = resourceTemplateProvider.provideTemplateForName(BASEVIEW_TEMPLATE);

                createMVPFiles(className + "Contract.java", contract
                        .replace("${CLASS_NAME}", className)
                        .replace("${PACKAGE_PATH}", mActivityPackage)
                        .replace("${BASE_PATH}", mPackage + ".base"));

                createMVPFiles(className + "Presenter.java", presenter
                        .replace("${CLASS_NAME}", className)
                        .replace("${PACKAGE_PATH}", mActivityPackage));

                createBaseFiles("BasePresenter.java", basePresenter.replace("${BASE_PATH}", mPackage + ".base"));

                createBaseFiles("BaseView.java", baseView.replace("${BASE_PATH}", mPackage + ".base"));

                refreshProject();

                Util.showNotification(mProject, MessageType.INFO, "生成完毕！");
            }
        });
    }
}
