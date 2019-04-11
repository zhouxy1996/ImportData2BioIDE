package org.big.service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.big.entityVO.ExcelUntilF;
import org.big.entityVO.NametypeEnum;

public interface BirdAddData {

	void importByExcel() throws Exception;
	/**
	 * 
	 * @Description 统计属、种阶元接受名和异名引证的数量
	 * @param response
	 * @author ZXY
	 */
	void countCitationByTaxon(HttpServletResponse response);
	
	void updateCitationStrBySciName(HttpServletResponse response) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, Exception;
	/**
	 * 
	 * @Description 使用正则表达式解析引证原文中的页码
	 * @param citationstr
	 * @return
	 * @author ZXY
	 */
	Map<String, String> getPageFromCitationStr(String citationstr);
	
	void initRegExPagelist();
	/**
	 * 
	 * @Description 2019鸟引证数据检验
	 * @author ZXY
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws Exception 
	 */
	void validateCitation() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, Exception;
	
	/**
	 * 
	 * @Description 查询某个数据集下所有的引证信息
	 * @param nametype 名称类型
	 * @param datasetId 数据集id
	 * @return
	 * @author ZXY
	 */
	List<ExcelUntilF> exportCitationExcelOfDataSet(NametypeEnum nametype, String datasetId);
	/**
	 * 
	 * @Description 接受名和异名 名称相同，命名信息相同，删除异名
	 * @author ZXY
	 */
	void deleteCitationOfSameSciname(String datasetId);

}
