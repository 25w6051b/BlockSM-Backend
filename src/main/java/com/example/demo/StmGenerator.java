package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.core.DiagramDescription;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class StmGenerator {
    public String color = "orange";

    // webサイトから受け取ったデータを画像に変換し返すメソッド
    public byte[] convertPumlToPngBytes(String plantUMLStr) throws IOException {
        SourceStringReader reader = new SourceStringReader(plantUMLStr);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {

            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
            // PNG画像データを出力先ストリームに書き込む
            // DiagramDescription desc = reader.outputImage(os);
            return os.toByteArray();
        }
    }

    // jsonのデータをplantUML記述に変換する関数
    public String jsonToPlantUML(JsonNode data) {
        StringBuilder result = new StringBuilder(); // plantUML形式の言語を入れる
        List<String> stateList = new ArrayList<>();
        List<String> nextStateList = new ArrayList<>();
        Set<String> allStateSet = new LinkedHashSet<>(); // 全「状態名」を保持するセット関数
        StringBuilder current = new StringBuilder();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode clickNode = mapper.createObjectNode(); // 今の状態がクリックされている時にそのブロックの名前とタイプを入れる
        ObjectNode clickedState = mapper.createObjectNode(); // 次の状態がクリックされている時にそのブロックの次の状態とタイプを入れる
        result.append("@startuml" + "\n" + "skinparam defaultFontName \"Noto Sans JP\"" + "\n");

        
        // 「初めは初期状態とする」ブロックが設置されているかを確認し，なければノートのみをreslutに代入する
        AtomicBoolean containInitialState = new AtomicBoolean(false); // はじめは「初期状態」とするブロックががあるかどうかを確認する
        AtomicBoolean stateAfterInitial = new AtomicBoolean(false); // 次の状態が定義されているかを確認する
        AtomicBoolean hasInvalid = new AtomicBoolean(false); // 数字・アルファベット・ひらがな・カタカナ・漢字以外を検出
        AtomicBoolean hasMultipleInitialTransitions = new AtomicBoolean(false); // 初期状態が複数の遷移を持つかを調べる
        AtomicBoolean hasDuplicateState = new AtomicBoolean(false); // 状態が複数定義されているかを調べる

        // 受け取ったデータからplantUMLに変換できるかを確認する
        data.forEach(item -> {
            String stateName = item.get("state").asText();
            JsonNode transition = item.get("transition");

            checkInitialTransitionCount(stateName, transition, hasMultipleInitialTransitions); // 初期状態からの遷移が複数あるか調べる
            checkInitialState(stateName, item, transition, containInitialState,stateAfterInitial); // 初期状態内のmyselfとnextstateのプロパティを確認する
            validateStateNames(stateName, transition, hasInvalid); // 状態名が命名規則に従っている調べる
            checkDuplicateState(item, hasDuplicateState); // 状態が複数回定義されているか調べる
            stateList.add(stateName); // 全ての状態名を取得
            collectNextStates(transition, nextStateList, current); // 全ての次の状態を取得
            getNextStateOnClick(clickedState,item); // クリックされている次の状態を記録
        });

        // 全ての状態をallStateSetに追加
        allStateSet.addAll(stateList);
        allStateSet.addAll(nextStateList);
        allStateSet = allStateSet.stream()
                .map(s -> s.replace("#FF5555", "")) // #FF5555 を削除
                .map(String::trim)                  // 前後の空白を削除（任意）
                .filter(s -> !s.isEmpty())          // 空文字は除外
                .collect(Collectors.toCollection(LinkedHashSet::new)); // 順序保持

        // 初期状態がないとそもそも図として表示させないようにする
        if (!containInitialState.get()) {
            result.append("note as InitialStateDefinitionError\n <b><color:red>\"初めは「初期状態」としてください\"\n end note\n");
        }
        // 状態名が命名規則に従っていない場合は図として表示させないようにする
        else if(hasInvalid.get()) {
            result.append("note as stateNameNamingError\n <b><color:red>\"「状態名」には数字・日本語・アルファベットのみ使用できます\"\n end note\n");
        }
        // 初期状態からの遷移が複数ある場合は，意味論に反するので警告を出す．
        else if(hasMultipleInitialTransitions.get()){
            result.append("note as InvalidStateTransitionError\n <b><color:red>\"「初期状態」から到達できるのは一つの状態だけです\"\n<b><color:red>\"「初期状態」には「イベント」を設定できません\"\n end note\n");
        }
        // 状態が複数定義されている場合
        else if(hasDuplicateState.get()){
            result.append("note as DuplicateStateDefinitionError\n <b><color:red>\"同じ状態は複数回定義できません\"\n end note\n");
        }
        // 初期状態があるとき
        else {
            // 初期状態からの遷移がない時はそもそも図として表示させないようにする
            if (!stateAfterInitial.get()) {
                result.append("note as InitialStateTransitionError\n <b><color:red>\"「初期状態」の次の状態を決めてください\"\n end note\n");
            }
            // 初期状態からの遷移があるとき
            else {
                // 全状態を定義する(plantUMLに追加)
                createTemporaryState(allStateSet,result);

                // 各状態に関しての遷移・振る舞いを定義
                data.forEach(item -> {
                    String state = item.get("state").asText();
                    JsonNode transition = item.get("transition");
                    JsonNode behavior = item.get("behavior");
                    JsonNode myself = item.get("myself"); // 初期状態のみ持つことができるプロパティ　それ以外ではnullとなる
                    JsonNode currentNode = item.get("current"); // 現在の状態とitemの状態が同じときtrueとなる
                    JsonNode click = item.get("click"); // クリックされているかを確認するプロパティ
                    if (click != null && click.has("type")){
//                        System.out.println("あああああああ" + click.get("type").asText());
                        clickNode.put("type",click.get("type").asText());
                        clickNode.put("stateName",state);
                    }

                    if ("初期状態".equals(state)) {
                        // 初期状態の処理
                        result.append(firstStateToPlantUML(state, myself, transition.get(0),click));
                    }
                    else if (state != null && !state.isEmpty()) {
                        // transitionの要素があればplantUmlを追加する
                        if (transition != null && !transition.isEmpty()) {
                            transition.forEach(child -> {
//                                JsonNode nextState = child.get("nextState");
                                if (child.has("nextState")) {
                                    result.append(transitionToPlantUML(state, child, click));
                                }
                            });
                        }
                        // behaviorの要素を持っているとき
                        if(!behavior.isEmpty()){
                            result.append(behaviorToPlantUML(state, behavior,click)); // behaviorの中身をplantUmlに追加する
                        }
                    }
                });
                updateStateColor(clickNode, current, result, clickedState); // 状態の色付けを行う
            }
        }
        result.append("@enduml");
        //System.out.println(result.toString());
        return result.toString();
    }


    // 初期状態かどうかを確認するメソッド
    private void checkInitialState(String stateName, JsonNode item, Iterable<JsonNode> transition, AtomicBoolean containInitialState, AtomicBoolean stateAfterInitial) {
        if ("初期状態".equals(stateName)) {
            JsonNode myself = item.get("myself");
            boolean condition = myself != null && myself.has("condition") && myself.get("condition").asBoolean();
            String nextState = "未定義";

            // 初期状態のmyself=trueの時
            if (condition) {
                containInitialState.set(true);
            }

            // 初期状態が次の状態を持っていた時
            for (JsonNode t : transition) {
                if (t.has("nextState")) {
                    // nextState = t.get("nextState").asText();
                    stateAfterInitial.set(true);
                }
            }
        }
    }

    // 次の状態が現在の状態と一致するか，またリストに追加するメソッド
    private void collectNextStates(JsonNode transition, List<String> nextStateList, StringBuilder current) {
        if (transition != null) {
            transition.forEach(tra -> {
                JsonNode nextStateNode = tra.get("nextState");
                if (nextStateNode != null) {
                    String nextState = nextStateNode.asText();
                    nextStateList.add(nextState); // 次の状態のリストに追加

                    // 現在の状態が「次の状態」であるときはcurrentに追加
                    if (nextState.contains("#FF5555") && current.length() == 0) {
                        current.append(nextState);
                    }
                }
            });
        }
    }


    // 状態が複数回定義されているかを調べるメソッド
    private void checkDuplicateState(JsonNode item, AtomicBoolean hasDuplicateState){
        JsonNode invalid = item.get("invalid");
        if (invalid != null) {
            JsonNode condition = invalid.get("condition");
            if (condition != null && condition.asText().equals("true")) {
                hasDuplicateState.set(true);
            }
        }
    }


    // 開始疑似状態に複数の遷移があるかを調べるメソッド
    public void checkInitialTransitionCount(String stateName, JsonNode transition, AtomicBoolean hasMultipleInitialTransitions) {
        if (stateName.equals("初期状態")){
            if (transition.size() >= 2){
                hasMultipleInitialTransitions.set(true);
            }
        }
    }


    // 状態名が命名規則に従っているか確認するメソッド
    private void validateStateNames(String stateName, JsonNode transition, AtomicBoolean hasInvalid) {

        // 共通の命名規則チェック
        final String INVALID_PATTERN = ".*[^0-9A-Za-zＡ-Ｚａ-ｚ０-９ぁ-んァ-ヶ一-龥々ー〇].*";


        String cleanedStateName = stateName.replace("#FF5555", ""); // 現在の状態の時#FF5555がつくときがあるがこれは無視
        // stateName のチェック
        if (cleanedStateName != null && cleanedStateName.matches(INVALID_PATTERN)) {
            hasInvalid.set(true);
        }

        // nextState のチェック
        transition.forEach(child -> {
            if (child.has("nextState")) {
                String nextState = child.get("nextState").asText();
                String cleanednextState = nextState.replace("#FF5555", ""); // 現在の状態の時#FF5555がつくときがあるがこれは無視
                if (cleanednextState.matches(INVALID_PATTERN)) {
                    hasInvalid.set(true);
                }
            }
        });
    }


    // 初期状態の時に呼び出されるメソッド
    public String firstStateToPlantUML(String state, JsonNode myself, JsonNode transition, JsonNode click) {
        String result = ""; // 返り値を代入する変数
        // そもそも初期状態のtransitionに何も入っていない場合(JsonNode next = transition.get("nextState")がエラーとなる可能性があるため)
        if(transition != null) {
            JsonNode next = transition.get("nextState");
            // 次の状態が存在しない限り初期状態を定義できない(そうなるとステートマシン図とも認識されない)
            if (next != null) {
                if(next.asText().equals("終了状態")){
                    result += "[*] " + transitionColor(click) + " [*]" ; // 次の状態が存在する場合
                }
                else {
                    result += "[*] " + transitionColor(click) + " " + next.asText(); // 次の状態が存在する場合
                }
            }
//            else {
//                result += "state 未定義 #text:red\n" + "[*] " + transitionColor(click) + " 未定義"; // 次の状態を未定義として無理やり定義
//            }

            // 初期状態にevent,guard,effectがあれば追加
            // ただしeventとguardは初期状態にあってはならないので赤色で追加
            String effectPart = "";
            if (transition.get("effect") != null) {
                effectPart = " / " + transition.get("effect").asText();
            }
            // イベント,ガード,エフェクトのいずれかがある場合
            if (!effectPart.isEmpty()) {
                result += " :" + effectPart;
            }
            result += "\n"; // event,guard,effectがある場合も考えられるため，ここで改行する

//            // 状態名に未定義が含まれている場合は赤色にする
//            if(next.asText().contains("未定義")){
//                result += undifinedStateToRed(next.asText()) + "\n";
//            }

            // 初期状態はイベントとガードを持てないため，持っていたらエラーを出す
            if (transition.get("event") != null || transition.get("guard") != null) {
                result += "note on link #FFF8DC\n" ;
                if (transition.get("event") != null) {
                    result += " 'InitialStateEventError\n <b><color:red> 「初期状態」には「イベント」を設定できません\n";
                }
                if (transition.get("guard") != null) {
                    result += " 'InitialStateConditionError\n <b><color:red> 「初期状態」には「条件」を設定できません\n";
                }
                result += "end note\n";
            }
        }
//        else {
//            result += "state 未定義 #text:red\n" + "[*] --> 未定義\n"; // 次の状態を未定義として無理やり定義
//        }

//        // 「初めは初期状態とする」ブロックが配置されていない場合はノートを付ける
//        if (!(myself.get("condition")).asBoolean()) {
//            result += "note on link #FFF8DC\n" + " <b><color:red>「初めは初期状態とする」ブロックを置いてください</color></b>\n" + "end note\n";
//        }
        return result;
    }

//    // 次の状態もresultに追加する
//    private void appendValidNextStates(List<String> nextStateList, List<String> stateList, StringBuilder current, StringBuilder result) {
//        for (String next : nextStateList) {
//            if (!stateList.contains(next) && !next.contains("未定義") && !current.toString().equals(next) && !next.contains("終了状態")) {
//                result.append("state ").append(next).append("\n");
//            }
//        }
//    }


    // 次の状態がクリックされているときに用いる関数
    public ObjectNode getNextStateOnClick (ObjectNode result, JsonNode item){
        JsonNode click = item.get("click");
        // クリックされているブロックがあるか調べる(numberを持つのは「次の状態」ブロックのみ)
        if(click != null && click.has("type") && click.has("number")){
            String clickValue = click.get("type").asText();
            int index = Integer.parseInt(click.get("number").asText());
            JsonNode transition = item.get("transition");
            JsonNode transitionIndex = transition.get(index);
            if(transitionIndex != null) {
                JsonNode nextState = transitionIndex.get("nextState");
                if (nextState != null) {
                    result.put("nextState", nextState.asText()); // 次の状態の名前を追加
                    if ("changeStateType".equals(clickValue)) {
                        result.put("type", "changeStateType");  // 「～という状態に変わる」ブロック
                    } else if ("stateDefinitionType".equals(clickValue)) {
                        result.put("type", "stateDefinitionType");  // 「状態名」ブロック
                    }
                }
            }
        }
        return  result;
    }


     // 遷移についてplantUMLに変換するメソッド
     public String transitionToPlantUML(String state,JsonNode transition,JsonNode click){
         String result = "";
         String eventPart = "";
         String guardPart = "";
         String effectPart = "";
         String nextState = transition.get("nextState").asText();
         if (transition.get("event") != null && !"completeEvent".equals(transition.get("event").asText())) {
             eventPart = " " + transition.get("event").asText();
         }
         if (transition.get("guard") != null) {
             guardPart = " [" + transition.get("guard").asText() + "]";
         }
         if (transition.get("effect") != null) {
             effectPart = " / " + transition.get("effect").asText();
         }
         if (nextState.equals("終了状態")){
             nextState = "[*]";
         }
         // イベント,ガード,エフェクトのいずれかがある場合
         if (!eventPart.isEmpty() || !guardPart.isEmpty() || !effectPart.isEmpty()) {
             result = state + transitionColor(click) + nextState + " :" + eventPart + guardPart + effectPart + "\n";
         }
         // イベント,ガード,エフェクトのいずれもない場合
         else {
             result = state + transitionColor(click) + nextState + "\n";
         }
//         if (state.contains("未定義")){
//             result += undifinedStateToRed(state);
//         }
         return result;
     }


     // クリック時，遷移に色づける関数
     public String transitionColor(JsonNode click){
         if (click == null || !click.has("type")) {
             return "-->";
         }
         String clickVaule = click.get("type").asText();
         if (clickVaule.equals("switchIfType") || clickVaule.equals("switchElseIfType")){
            return "-[#" + color +"]->";
         }
         return "-->";
     }


    // 振る舞いについてplantUMLに変換するメソッド
     public String behaviorToPlantUML(String state,JsonNode behavior,JsonNode click) {
         String result = "";
         // エントリがある場合
         if (behavior.get("entry") != null ) {
             result += state + " : " + behaviorRuleColor("entry",click) + " / " + behavior.get("entry").asText() + "\n";
         }
         // ドウがある場合
         if (behavior.get("do") != null ) {
             result += state + " : " + behaviorRuleColor("do",click) + " / " + behavior.get("do").asText() + "\n";
         }
         // イグジットがある場合
         if (behavior.get("exit") != null ) {
             result += state + " : " + behaviorRuleColor("exit",click) + " / " + behavior.get("exit").asText() + "\n";
         }
         return result;
     }


    // クリック時，behaviorRuleに色付けを行う関数
    public String behaviorRuleColor(String behaviorRule, JsonNode click) {
        // clickがnull または typeが無ければそのまま返す
        if (click == null || !click.has("type")) {
            return behaviorRule;
        }

        // typeを取り出す
        String type = click.get("type").asText();

        // typeをルール名にマッピング
        String mapped = null;
        switch (type) {
            case "entryType":        mapped = "entry"; break;
            case "doContinuousType":
            case "doOnetimeType":    mapped = "do";    break;
            case "exitType":         mapped = "exit";  break;
        }

        // マッピング結果が一致すれば色付け
        if (behaviorRule.equals(mapped)) {
            return "<color:" + color + ">" + behaviorRule + "<color:black>";
        }
        return behaviorRule;
    }


    // 未定義の状態に赤色で色付けを行うメソッド
//     public String undifinedStateToRed(String stateName) {
//        String result = "";
//        if (stateName.contains("未定義")) {
//            result += "state " + stateName + " #text:red\n";
//        }
//        return result;
//     }
//
//     // 状態の色を変える関数(クリック時)
//     public String clickedStateColor(JsonNode currentNode, JsonNode click) {
//         // クリックされていないとき
//         if (click == null || !click.has("type")) {
//             return "\n";
//         }
//
//         // クリックされているとき
//         String prefix = (currentNode != null) ? "#FF5555;" : "#"; // currentNodeがあれば;なければ#出始める
//         String clickValue = click.get("type").asText(); // ブロックのタイプが入る
//
//         // ブロックのタイプを確認する
//         switch (clickValue) {
//             case "stateActionType":
//                 return prefix + "line:" + color + ";line.bold\n";
//             case "stateDefinitionType":
//                 return prefix + "text:" + color + "\n";
//             default:
//                 return "\n";
//         }
//     }

//     public String clickedNextStateColor(StringBuilder current, ObjectNode clickState) {
//         // クリックされていないとき
//         if (clickState == null || !clickState.has("type")) {
//             return "";
//         }
//
//         // クリックされているとき
//         String nextState = clickState.get("nextState").asText();
//         String clickType = clickState.get("type").asText(); // ブロックのタイプが入る
//         String undifiendText = nextState.contains("未定義") ? ";text:red" : "";
//         // ブロックのタイプを確認する
//         if(!nextState.equals("終了状態")) {
//             if (!current.toString().equals(nextState)) {
//                 switch (clickType) {
//                     case "changeStateType":
//                         return "state " + nextState + " #line:" + color + ";line.bold" + undifiendText + ";\n";
//                     case "stateDefinitionType":
//                         return "state " + nextState + " #text:" + color + ";\n";
//                 }
//             } else {
//                 switch (clickType) {
//                     case "changeStateType":
//                         return "state " + nextState + ";line:" + color + ";line.bold;" + undifiendText + "\n";
//                     case "stateDefinitionType":
//                         return "state " + nextState + ";text:" + color + ";\n";
//                 }
//             }
//         }
//         return "";
//     }

     // 初めに状態名(次の状態も含む)を全て追加するメソッド
     public StringBuilder createTemporaryState(Set<String> allStateSet, StringBuilder result){
        // 初期状態か終了状態でなければ状態名を追加する
        for (String stateName : allStateSet) {
             if (!stateName.equals("初期状態") && !stateName.equals("終了状態")) {
                 // 状態名に「未定義」を含む場合は赤色で定義する
                 if (stateName.contains("未定義")) {
                     result.append("state " + stateName + "#text:red\n");
                 } else {
                     result.append("state " + stateName + "\n");
                 }
             }
         }
         return result;
     }

    public StringBuilder updateStateColor(ObjectNode click, StringBuilder current, StringBuilder result, ObjectNode clickedState) {
        String clickType = click.size() > 0 ? click.get("type").asText() : "";
        String stateName = null;
        boolean isCurrent = false;

        // 次の状態か今の状態かを判定
        if (clickedState.size() > 0) {
            stateName = clickedState.get("nextState").asText();
            isCurrent = current.length() > 0 && current.toString().equals(stateName);
        } else if (click.has("stateName")) {
            stateName = click.get("stateName").asText();
            isCurrent = current.length() > 0 && stateName.equals(current.toString());
        }

        if (stateName != null) {
            if ("終了状態".equals(stateName)) {
                return result; // 何も追加せずに返す
            }

            boolean isUndefined = stateName.contains("未定義"); // 「未定義」の状態が含まれるかを確認する

            switch (clickType) {
                case "changeStateType":
                    result.append("state " + stateName + (isCurrent ? ";line:" : " #line:") + color
                            + ";line.bold" + (isUndefined ? ";text:red" : "") + "\n");
                    break;

                case "stateDefinitionType":
                    result.append("state " + stateName + (isCurrent ? ";text:" : " #text:") + color + "\n");
                    break;

                case "stateActionType":
                    result.append("state " + stateName + (isCurrent ? " #FF5555;line:" : " #line:") + color
                            + ";line.bold" + (isUndefined ? ";text:red" : "") + "\n");
                    break;
            }
        }

        return result;
    }
}

