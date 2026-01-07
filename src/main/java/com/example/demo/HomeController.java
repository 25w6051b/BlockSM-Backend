// src/main/java/com/example/demo/HomeController.java
package com.example.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;

@RestController// このクラスがデータを返すと明示する つまり自動でjson形式で返してくれる
@CrossOrigin(origins = "*")  // すべてのオリジンを許可
public class HomeController {
    @GetMapping("/")
    public ResponseEntity<JsonNode> home() {
        String jsonDirPath = "./json"; // jsonファイルが入ったパスを指定
        ObjectMapper objectMapper = new ObjectMapper();
        File rootDir = new File(jsonDirPath); // ディレクトリ情報を表すオブジェクトを作成
        File[] taskDirs = rootDir.listFiles(File::isDirectory); // ディレクトリ内にあるファイルを配列として返す
        Arrays.sort(taskDirs, Comparator.comparing(File::getName));
        ObjectNode mergedJson = objectMapper.createObjectNode(); // jsonファイルの中身をまとめて送るためのオブジェクト

        try {
            // 各タスクごとにjsonファイルを確認
            for (File taskDir : taskDirs) {
                File[] jsonFile = taskDir.listFiles(file -> file.isFile() && file.getName().endsWith(".json")); // 各タスクファイル下のjsonファイルを取得
                // jsonファイルが一つだけ存在するかを確認
                if (jsonFile != null && jsonFile.length == 1) {
                    JsonNode parsedJson = objectMapper.readTree(jsonFile[0]); // JSONファイルを直接JsonNodeとしてパース
                    String taskName = taskDir.getName(); // タスクの名前(フォルダ名)を取得
                    mergedJson.set(taskName, parsedJson); // jsonファイルの中身を結合
                }
                else{
                    System.out.println("jsonファイルが一つだけでない可能性があります : " + taskDir.getName());
                }
            }
            // パースしたJSONをレスポンスとして返す
            return ResponseEntity.ok(mergedJson);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);  // HTTP 500（内部サーバーエラー）を返す
        }
    }

    @PostMapping("/")
    public ResponseEntity<DiagramResponse> postModelData(@RequestBody JsonNode data) throws IOException {
        StmGenerator generator = new StmGenerator();
//        System.out.println(data); // 受け取ったワークスペースの情報
        String plantUMLValue = generator.jsonToPlantUML(data); // plantUml形式に変換
//        System.out.println(plantUMLValue);
//        System.out.println("");
        byte[] imageBytes = generator.convertPumlToPngBytes(plantUMLValue);

        // 画像をBase64(テキスト形式)に変換
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        DiagramResponse response = new DiagramResponse(plantUMLValue, base64Image);

        // PNG画像と認識してもらうためにヘッダーをつける
        // HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.IMAGE_PNG);

//        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
        return new ResponseEntity<>(response,HttpStatus.OK);
    }
}



//    @GetMapping("/image")
//    public ResponseEntity<byte[]> getImage() throws IOException {
//
////
////        File imageFile = new File("C:\\Users\\Dell\\researchTools\\demo\\demo\\input.img");
////        byte[] imageBytes = java.nio.file.Files.readAllBytes(imageFile.toPath());
////
////        return ResponseEntity.ok()
////                .header("Content-Type", "image/png")
////                .body(imageBytes);
//    }

//
//    @PostMapping("/")
//    public String saveWorkspace(@RequestBody Map<String , String> receiveData) {
//        String workSpaceData = receiveData.get("workSpaceXml");
//        return "okaaaa";
//    }

