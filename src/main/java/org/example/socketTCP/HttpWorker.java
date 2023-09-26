package org.example.socketTCP;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HttpWorker extends Thread {

    Socket socket;
    String clientRequest;

    /**
     * Construtor
     * 
     * @param s, socket a ser monitorado
     */
    public HttpWorker(String req, Socket s) {
        socket = s;
        clientRequest = req;
    }

    /**
     * Instância a ser executada. Executa concorrentemente.
     */
    public void run() {
        try {
            LogUtil.clear();

            PrintStream printer = new PrintStream(socket.getOutputStream());

            LogUtil.write("");
            LogUtil.write("HttpWorker está trabalhando...");
            LogUtil.write(clientRequest);

            if (!clientRequest.startsWith("GET") || clientRequest.length() < 14 ||
                    !(clientRequest.endsWith("HTTP/1.0") || clientRequest.endsWith("HTTP/1.1"))) {
                LogUtil.write("400(Bad Request): " + clientRequest);
                String errorPage = buildErrorPage("400", "Bad Request",
                        "Your browser sent a request that this server could not understand.");
                printer.println(errorPage);
            } else {
                String req = clientRequest.substring(4, clientRequest.length() - 9).trim();

                Pattern pattern = Pattern.compile("/[\\.].*");
                Matcher matcher = pattern.matcher(req);

                if (req.indexOf("..") > -1 ||
                        req.indexOf("/.ht") > -1 ||
                        req.endsWith("~") ||
                        matcher.matches()) {
                    LogUtil.write("403(Forbidden): " + req);
                    String errorPage = buildErrorPage("403", "Forbidden",
                            "You don't have permission to access the requested URL " + req);
                    printer.println(errorPage);
                } else {
                    // Decodifica ASCII
                    req = URLDecoder.decode(req, "UTF-8");

                    if (req.endsWith("/")) {
                        req = req.substring(0, req.length() - 1); // remove / final
                    }
                    // Requisições únicas
                    if (req.indexOf(".") > -1) { // Requisição para único arquivo

                        if (!req.startsWith("/images/") && !req.startsWith("/favicon.ico")) {
                            LogUtil.write("> This is a [SINGLE FILE] request..");
                        }
                        handleFileRequest(req, printer);
                    } else { // Request for directory
                        LogUtil.write("> This is a [DIRECTORY EXPLORE] request..");
                        handleExploreRequest(req, printer);
                    }
                }
            }
            LogUtil.save(true);
            socket.close();
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    /**
     * Gerencia requisição para único arquivo
     * 
     * @param req,     req do cliente
     * @param printer, printa na saída
     */
    private void handleFileRequest(String req, PrintStream printer) throws FileNotFoundException, IOException {
        // Recebe a raiz do servidor
        String rootDir = getRootFolder();

        // Caminho real
        String path = Paths.get(rootDir, req).toString();

        File file = new File(path);

        if (!file.exists() || !file.isFile()) {
            printer.println("No such resource:" + req);
            LogUtil.write(">> No such resource:" + req);
        } else {
            if (!req.startsWith("/images/") && !req.startsWith("/favicon.ico")) {
                LogUtil.write(">> Seek the content of file: " + file.getName());
            }
            // Print header
            String htmlHeader = buildHttpHeader(path, file.length());
            printer.println(htmlHeader);

            // Open file to input stream
            InputStream fs = new FileInputStream(file);
            byte[] buffer = new byte[1000];

            while (fs.available() > 0) {
                printer.write(buffer, 0, fs.read(buffer));
            }
            fs.close();
        }
    }

    /**
     * Gerencia exploração de arquivo e diretório
     * 
     * @param req,     req do cliente
     * @param printer, printa na saída
     */
    private void handleExploreRequest(String req, PrintStream printer) {
        String rootDir = getRootFolder();

        String path = Paths.get(rootDir, req).toString();

        File file = new File(path);

        if (!file.exists()) {
            printer.println("No such resource:" + req);
            LogUtil.write(">> No such resource:" + req);
        } else {
            LogUtil.write(">> Explore the content under folder: " + file.getName());
            File[] files = file.listFiles();
            Arrays.sort(files);

            // Constroi estrutura do diretório em HTML
            StringBuilder sbDirHtml = new StringBuilder();
            // StringBuilder controi string dinamicamente e é mais eficiente
            // Título da tabela
            sbDirHtml.append("<table>");
            sbDirHtml.append("<tr>");
            sbDirHtml.append("  <th>Name</th>");
            sbDirHtml.append("  <th>Last Modified</th>");
            sbDirHtml.append("  <th>Size(Bytes)</th>");
            sbDirHtml.append("</tr>");

            if (!path.equals(rootDir)) {
                String parent = path.substring(0, path.lastIndexOf(File.separator));
                if (parent.equals(rootDir)) {
                    parent = "../";
                } else {
                    parent = parent.replace(rootDir, "");
                }
                parent = parent.replace("\\", "/");
                // Mostra o diretoŕio anterior (..)
                sbDirHtml.append("<tr>");
                sbDirHtml.append("  <td><img src=\"" + buildImageLink(req, "images/folder.png") + "\"></img><a href=\""
                        + parent + "\">../</a></td>");
                sbDirHtml.append("  <td></td>");
                sbDirHtml.append("  <td></td>");
                sbDirHtml.append("</tr>");
            }

            // Linhas para diretório
            List<File> folders = getFileByType(files, true);
            for (File folder : folders) {
                LogUtil.write(">>> Directory: " + folder.getName());
                sbDirHtml.append("<tr>");
                sbDirHtml.append("  <td><img src=\"" + buildImageLink(req, "images/folder.png") + "\"></img><a href=\""
                        + buildRelativeLink(req, folder.getName()) + "\">" + folder.getName() + "</a></td>");
                sbDirHtml.append("  <td>" + getFormattedDate(folder.lastModified()) + "</td>");
                sbDirHtml.append("  <td></td>");
                sbDirHtml.append("</tr>");
            }
            // Linhas para arquivos
            List<File> fileList = getFileByType(files, false);
            for (File f : fileList) {
                LogUtil.write(">>> File: " + f.getName());
                sbDirHtml.append("<tr>");
                sbDirHtml.append("  <td><img src=\"" + buildImageLink(req, getFileImage(f.getName()))
                        + "\" width=\"16\"></img><a href=\"" + buildRelativeLink(req, f.getName()) + "\">" + f.getName()
                        + "</a></td>");
                sbDirHtml.append("  <td>" + getFormattedDate(f.lastModified()) + "</td>");
                sbDirHtml.append("  <td>" + f.length() + "</td>");
                sbDirHtml.append("</tr>");
            }

            sbDirHtml.append("</table>");
            String htmlPage = buildHtmlPage(sbDirHtml.toString(), "");
            String htmlHeader = buildHttpHeader(path, htmlPage.length());
            printer.println(htmlHeader);
            printer.println(htmlPage);
        }
    }

    /**
     * Build http header
     * 
     * @param path,   caminho da requisição
     * @param length, tamanho do conteúdo
     *                @return, header text
     */
    private String buildHttpHeader(String path, long length) {
        String CRLF = "\r\n";

        StringBuilder sbHtml = new StringBuilder();
        sbHtml.append("HTTP/1.1 200 OK");
        sbHtml.append(CRLF);
        sbHtml.append("Content-Length: " + length);
        sbHtml.append(CRLF);
        sbHtml.append("Content-Type: " + getContentType(path));
        sbHtml.append(CRLF);
        return sbHtml.toString();
    }

    /**
     * Constroi página HTTP
     * 
     * @param content, cconteúdo da página
     * @param header1, conteúdo h1
     *                 @return, page text
     */
    private String buildHtmlPage(String content, String header) {
        StringBuilder sbHtml = new StringBuilder();
        sbHtml.append("<!DOCTYPE html>");
        sbHtml.append("<html>");
        sbHtml.append("<head>");
        sbHtml.append("<style>");
        sbHtml.append(" table { width:50%; } ");
        sbHtml.append(" th, td { padding: 3px; text-align: left; }");
        sbHtml.append("</style>");
        sbHtml.append("<title>Meu Web Server</title>");
        sbHtml.append("</head>");
        sbHtml.append("<body>");
        if (header != null && !header.isEmpty()) {
            sbHtml.append("<h1>" + header + "</h1>");
        } else {
            sbHtml.append("<h1>File Explorer in Web Server </h1>");
        }
        sbHtml.append(content);
        sbHtml.append("<hr>");
        sbHtml.append("</body>");
        sbHtml.append("</html>");
        return sbHtml.toString();
    }

    /**
     * Constroi página de erro para Bad Request
     * 
     * @param code,  http code: 400, 301, 200
     * @param title, título
     * @param msg,   mensagem de erro
     *               @return, page text
     */
    private String buildErrorPage(String code, String title, String msg) {
        StringBuilder sbHtml = new StringBuilder();
        sbHtml.append("HTTP/1.1 " + code + " " + title + "\r\n\r\n");
        sbHtml.append("<!DOCTYPE html>");
        sbHtml.append("<html>");
        sbHtml.append("<head>");
        sbHtml.append("<title>" + code + " " + title + "</title>");
        sbHtml.append("</head>");
        sbHtml.append("<body>");
        sbHtml.append("<h1>" + code + " " + title + "</h1>");
        sbHtml.append("<p>" + msg + "</p>");
        sbHtml.append("<hr>");
        sbHtml.append("</body>");
        sbHtml.append("</html>");
        return sbHtml.toString();
    }

    /**
     * Get lista de arquivo ou diretório
     * 
     * @param filelist, lista original
     * @param isfolder, flag se é arquivo ou diretório
     *                  @return, lista de arquivo/diretório
     */
    private List<File> getFileByType(File[] filelist, boolean isfolder) {
        List<File> files = new ArrayList<File>();
        if (filelist == null || filelist.length == 0) {
            return files;
        }

        for (int i = 0; i < filelist.length; i++) {
            if (filelist[i].isDirectory() && isfolder) {
                files.add(filelist[i]);
            } else if (filelist[i].isFile() && !isfolder) {
                files.add(filelist[i]);
            }
        }
        return files;
    }

    /**
     * Get caminho raíz
     * @return, caminho do local atual
     */
    private String getRootFolder() {
        String root = "";
        try {
            File f = new File(".");
            root = f.getCanonicalPath();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return root;
    }

    /**
     * Converte data
     * 
     * @param lastmodified, long representando data
     *                      @return, data formatada em String
     */
    private String getFormattedDate(long lastmodified) {
        if (lastmodified < 0) {
            return "";
        }

        Date lm = new Date(lastmodified);
        String lasmod = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lm);
        return lasmod;
    }

    /**
     * Constroi Link relativo
     * 
     * @param current,  requisição atual
     * @param filename, nome arquivo
     *                  @return, nome do arquivo formatado
     */
    private String buildRelativeLink(String req, String filename) {
        if (req == null || req.equals("") || req.equals("/")) {
            return filename;
        } else {
            return req + "/" + filename;
        }
    }

    /**
     * Constroi links de imagem formatados
     * 
     * @param current,  requisição tual
     * @param filename, nome do arquivo
     *                  @return, nome do arquivo formatado
     */
    private String buildImageLink(String req, String filename) {
        if (req == null || req.equals("") || req.equals("/")) {
            return filename;
        } else {
            String imageLink = filename;
            for (int i = 0; i < req.length(); i++) {
                if (req.charAt(i) == '/') {
                    // Subir na árvore
                    imageLink = "../" + imageLink;
                }
            }
            return imageLink;
        }
    }

    /**
     * Get icone do arquivo de acordo com a extensão
     * 
     * @param path, caminho do arquivo
     *              @return, caminho do ícone
     */
    private static String getFileImage(String path) {
        if (path == null || path.equals("") || path.lastIndexOf(".") < 0) {
            return "images/file.png";
        }

        String extension = path.substring(path.lastIndexOf("."));
        switch (extension) {
            case ".class":
                return "images/class.png";
            case ".html":
                return "images/html.png";
            case ".java":
                return "images/java.png";
            case ".txt":
                return "images/text.png";
            case ".xml":
                return "images/xml.png";
            default:
                return "images/file.png";
        }
    }

    /**
     * Get MIME type according to file extension
     * 
     * @param path, file path
     *              @return, MIME type
     */
    private static String getContentType(String path) {
        if (path == null || path.equals("") || path.lastIndexOf(".") < 0) {
            return "text/html";
        }

        String extension = path.substring(path.lastIndexOf("."));
        switch (extension) {
            case ".html":
            case ".htm":
                return "text/html";
            case ".txt":
                return "text/plain";
            case ".png":
                return "image/png";
            case ".jpg":
                return "image/jpg";
            case ".ico":
                return "image/x-icon .ico";
            case ".wml":
                return "text/html"; // text/vnd.wap.wml
            default:
                return "text/plain";
        }
    }
}