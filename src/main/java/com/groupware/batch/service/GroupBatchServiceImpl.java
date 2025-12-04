package com.groupware.batch.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.groupware.dao.BatchExecutionHistoryDao;

/**
* UserServiceImpl
* Userサービス
* @author　N.Hirai
* @version　1.0.0
*/
@Service
public class GroupBatchServiceImpl implements GroupBatchService {
	@Autowired
	private BatchExecutionHistoryDao batchExecutionHistoryDao;

	/**
	* バッチ実行履歴を作成する
	* @param　batch_name バッチ名称
	* @param　result 結果
	* @return　
	*/
	@Override
	public void insert(String batch_name, boolean result) {
		batchExecutionHistoryDao.insert(batch_name, result);
	}

	/**
	* 対象バッチが実行月に起動成功したかチェック 
	* 
	* @param　batch_name バッチ名称
	* @return　boolean
	*/
	@Override
	public boolean isBatchExecution(String batch_name) {
		return batchExecutionHistoryDao.isBatchExecution(batch_name);
	}
}
