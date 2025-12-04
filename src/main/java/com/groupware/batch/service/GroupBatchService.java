package com.groupware.batch.service;

/**
* GroupBatchService
* GroupBatchサービスインターフェース
* @author　N.Hirai
* @version　1.0.0
*/
public interface GroupBatchService {
	/**
	* バッチ実行履歴を作成する
	* @param　batch_name バッチ名称
	* @param　result 結果
	* @return　
	*/
	void insert(String batch_name, boolean result);

	/**
	* 対象バッチが実行月に起動成功したかチェック 
	* 
	* @param　batch_name バッチ名称
	* @return　boolean
	*/
	boolean isBatchExecution(String batch_name);
}
