package org.big.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.big.common.CommUtils;
import org.big.common.EntityInit;
import org.big.common.ExcelUtil;
import org.big.common.HttpUtils;
import org.big.entity.Citation;
import org.big.entity.Commonname;
import org.big.entity.Datasource;
import org.big.entity.Descriptiontype;
import org.big.entity.Rank;
import org.big.entity.Ref;
import org.big.entity.Taxon;
import org.big.entityVO.BaseParamsForm;
import org.big.entityVO.LanguageEnum;
import org.big.entityVO.NametypeEnum;
import org.big.entityVO.PlantEncyclopediaExcelVO;
import org.big.entityVO.RankEnum;
import org.big.entityVO.RefTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cn.afterturn.easypoi.excel.ExcelImportUtil;
import cn.afterturn.easypoi.excel.entity.ImportParams;
import cn.afterturn.easypoi.excel.entity.result.ExcelImportResult;

@Service
public class PlantAsyncServiceImpl implements PlantAsyncService {

	@Autowired
	private DatasourceService datasourceService;
	@Autowired
	private TaxonService taxonService;
	@Autowired
	private ToolService toolService;
	@Autowired
	private RefService refService;
	@Autowired
	private CitationService citationService;
	@Autowired
	private CommonnameService commonnameService;
	@Autowired
	private DescriptiontypeService descriptiontypeService;
	@Autowired
	private DescriptionService descriptionService;
	private final String JsonExpertName = "expert";// 专家信息
	private final String ClassificationConcept = "ClassificationConcept";// 分类概念依据
	private final String relativeExcelPath = "path";
	private final String refRemark = "金效华植物百科";
	private final List<String> unColADescTitle = new ArrayList<>(
			Arrays.asList("分布信息", "生物学信息", "形态信息", "保护信息", "多媒体", "经济意义（价值）", "遗传信息", "文献信息", "专家信息", "v", "淡黄白色，"));
	private final List<String> unColBDescTitle = new ArrayList<>(Arrays.asList("图片", "线条图", "视频"));

	private final String[] notReadSheetNames = { "分布数据", "物种名录（sp2000）" };
	private final static Logger logger = LoggerFactory.getLogger(PlantAsyncServiceImpl.class);
	private final String url = "http://www.zoology.csdb.cn/WebServices/taxonNameParser";

	/**
	 * 
	 * @Description
	 * @Async注解 表明该方法是异步方法，如果注解在类上，那表明这个类里面的所有方法都是异步的。
	 * @param baseParamsForm
	 * @param partFiles      excel文件绝对路径集合
	 * @param map
	 * @throws Exception
	 * @author ZXY
	 */
	@Async
	public void insertListExcel(BaseParamsForm baseParamsForm, List<String> partFiles, Map<String, String> map)
			throws Exception {
		int activeCount = Thread.currentThread().getThreadGroup().activeCount();
		int maxPriority = Thread.currentThread().getThreadGroup().getMaxPriority();
		logger.info("线程" + Thread.currentThread().getName() +",activeCount = "+activeCount+",maxPriority"+maxPriority+ ", 执行异步任务：" + CommUtils.getCurrentDate());
		List<String> notReadSheetNamesAsList = Arrays.asList(notReadSheetNames);
		int j = 0;
		for (String path : partFiles) {
			j++;
			// read an excel(xls or xlsx,Multiple sheet), convert to entities
			Map<String, List<PlantEncyclopediaExcelVO>> excelMap = null;
			XSSFWorkbook xlsxWorkBook = null;
			HSSFWorkbook xlsWorkBook = null;
			try {
				if (path.contains("~$") || path.contains("desktop.ini")) {
					// 临时文件，目的是为了防止文档信息丢失
					// 意外断电，也会造成那些文档不自动消失，也会像正常文件一样始终保存在电脑中
					// 此类文件不处理
				} else if (path.endsWith(".xlsx")) {
					xlsxWorkBook = ExcelUtil.getXlsxWorkBook(path);
					excelMap = handleXSSFWorkbook(xlsxWorkBook, path, baseParamsForm, map, notReadSheetNamesAsList);
				} else if (path.endsWith(".xls")) {
					xlsWorkBook = ExcelUtil.getXlsWorkBook(path);
					excelMap = handleXSSFWorkbook(xlsWorkBook, path, baseParamsForm, map, notReadSheetNamesAsList);
					
				} else {
					logger.info("error F00001 无法识别的文件" + path);
				}
			} catch (Exception e) {
				logger.info("error F00002 无法识别的文件" + path);
			}finally {
				//关闭
				if(xlsWorkBook != null) {
					xlsWorkBook.close();
				}
				if(xlsxWorkBook != null) {
					xlsxWorkBook.close();
				}
			}
			// handle an excel（include multiple sheets）
			if (excelMap != null) {
				try {
					insertAnExcel(excelMap, path, baseParamsForm);
				} catch (Exception e) {
					logger.info("error 00000 ,错误信息如下" + e.getMessage() + "，路径：" + path);
					e.printStackTrace();
				}
			}
//			break;// test run
			if(j%50==0) {
				logger.info("线程" + Thread.currentThread().getName() + " 执行异步任务进度报告。已经完成："+j +"，总数:"+partFiles.size()+" , 百分比："+(j*100/partFiles.size())+"% ,"+ CommUtils.getCurrentDate());
			}

		}
		int activeCount1 = Thread.currentThread().getThreadGroup().activeCount();
		int maxPriority1 = Thread.currentThread().getThreadGroup().getMaxPriority();
		logger.info("线程" + Thread.currentThread().getName() +",activeCount = "+activeCount1+",maxPriority"+maxPriority1+ ", 执行异步任务：" + CommUtils.getCurrentDate());
	}

	/**
	 * xlsx title: PlantEncyclopediaServiceImpl.java Overloading
	 * 
	 * @param xlsWorkBook
	 * @param path
	 * @param baseParamsForm
	 * @author ZXY
	 * @throws Exception
	 * @throws FileNotFoundException
	 */
	private Map<String, List<PlantEncyclopediaExcelVO>> handleXSSFWorkbook(HSSFWorkbook xlsWorkBook, String path,
			BaseParamsForm baseParamsForm, Map<String, String> map, List<String> notReadSheetNames)
			throws FileNotFoundException, Exception {
		Map<String, List<PlantEncyclopediaExcelVO>> excelMap = new HashMap<>();
		// 循环工作表
		for (int numSheet = 0; numSheet < xlsWorkBook.getNumberOfSheets(); numSheet++) {
			HSSFSheet sheet = xlsWorkBook.getSheetAt(numSheet);
			if (sheet == null) {
				continue;
			}
			String currentSheetName = sheet.getSheetName().trim();
			handleSheet(currentSheetName, notReadSheetNames, path, numSheet, map, excelMap);
		}
		return excelMap;

	}

	/**
	 * xls title: PlantEncyclopediaServiceImpl.java Overloading
	 * 
	 * @param xlsxWorkBook
	 * @param path
	 * @param baseParamsForm
	 * @author ZXY
	 * @throws Exception
	 * @throws FileNotFoundException
	 */
	private Map<String, List<PlantEncyclopediaExcelVO>> handleXSSFWorkbook(XSSFWorkbook xlsxWorkBook, String path,
			BaseParamsForm baseParamsForm, Map<String, String> map, List<String> notReadSheetNames)
			throws FileNotFoundException, Exception {
		Map<String, List<PlantEncyclopediaExcelVO>> excelMap = new HashMap<>();
		// 循环工作表
		for (int numSheet = 0; numSheet < xlsxWorkBook.getNumberOfSheets(); numSheet++) {
			XSSFSheet xssfSheet = xlsxWorkBook.getSheetAt(numSheet);
			if (xssfSheet == null) {
				continue;
			}
			String currentSheetName = xssfSheet.getSheetName().trim();
			handleSheet(currentSheetName, notReadSheetNames, path, numSheet, map, excelMap);

		}
		return excelMap;

	}

	/**
	 * 
	 * title: PlantEncyclopediaServiceImpl.java 处理一个sheet
	 * 
	 * @param currentSheetName
	 * @param notReadSheetNames
	 * @param path
	 * @param numSheet
	 * @param map
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @author ZXY
	 */
	private void handleSheet(String currentSheetName, List<String> notReadSheetNames, String path, int numSheet,
			Map<String, String> map, Map<String, List<PlantEncyclopediaExcelVO>> excelMap)
			throws FileNotFoundException, Exception {
		ImportParams params = new ImportParams();
		// notReadSheetNames中的数据跳过，不读取
		if (notReadSheetNames.contains(currentSheetName)) {
			return;
		}
//		synchronized (map) {
//			map.put(currentSheetName, path);
//		}
		// 表头行数,默认1
		params.setHeadRows(0);
		// 表格标题行数,默认0
		params.setTitleRows(0);
		// 第几个sheet页
		params.setStartSheetIndex(numSheet);
		ExcelImportResult<PlantEncyclopediaExcelVO> result = ExcelImportUtil.importExcelMore(new FileInputStream(path),
				PlantEncyclopediaExcelVO.class, params);
		List<PlantEncyclopediaExcelVO> list = result.getList();
		excelMap.put(currentSheetName, list);
	}

	/**
	 * 
	 * title: PlantEncyclopediaServiceImpl.java
	 * 
	 * @param excelMap
	 * @author ZXY
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	private void insertAnExcel(Map<String, List<PlantEncyclopediaExcelVO>> excelMap, String path, BaseParamsForm params)
			throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, InstantiationException {
		Iterator<Entry<String, List<PlantEncyclopediaExcelVO>>> entries = excelMap.entrySet().iterator();
		Taxon taxon = new Taxon();
		EntityInit.initTaxon(taxon, params);// 初始化taxon属性值
		// 1. save 一次 taxon,整个excel文件读取完毕后再更新一次taxon
		if (params.isInsert()) {
			taxonService.saveOne(taxon);
		}
		// 2. 根据sheet名称逐一读取
		while (entries.hasNext()) {
			Entry<String, List<PlantEncyclopediaExcelVO>> entry = entries.next();
			String sheetName = entry.getKey();
			List<PlantEncyclopediaExcelVO> sheetValues = entry.getValue();
			if (sheetName.contains("百科") || sheetName.contains("Sheet1")) {
				insertBaiKeSheet(sheetValues, taxon, path, params);
			} else if (sheetName.contains("性状") || "一般被子植物".equals(sheetName) || "Sheet2".equals(sheetName)) {

			} else {
				if (!sheetName.contains("Sheet3")) {
					logger.info("error A00001, 未知sheet,sheetName=" + sheetName + ",path=" + path);
				}
			}
		}
		// 3. excel文件所有sheet处理完毕，最后更新一次taxon
		if (params.isInsert() && StringUtils.isNotEmpty(taxon.getScientificname())
				&& taxon.getRankid()!=0) {
			taxonService.saveOne(taxon);
		}

	}

	private void insertBaiKeSheet(List<PlantEncyclopediaExcelVO> sheetValues, Taxon taxon, String path,
			BaseParamsForm params) throws NoSuchMethodException, SecurityException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, InstantiationException {
		// 百科sheet
		for (PlantEncyclopediaExcelVO row : sheetValues) {
			boolean entityAttrNull = toolService.EntityAttrNull(row);// 判断是否所有属性值均为空
			// 如果为空行，跳过
			if (entityAttrNull) {
				continue;
			}
			toolService.reflectChangeValue(row, "*", "");// 修改值的等于*的属性值为空
			// 以colA为标题录入数据
			if (StringUtils.isNoneEmpty(row.getColA())) {
				insertByColA(row, taxon, path, params);
			} else if (StringUtils.isNoneEmpty(row.getColB())) {// 以colB为标题录入数据
				insertByColB(row, taxon, path, params);
			}
		}
		// 更新分类等级
		Rank rank = new Rank();
		rank.setId(String.valueOf(taxon.getRankid()));
		taxon.setRank(rank);
		if (params.isInsert()) {
			// update,读取整个excel完成后，更新实体信息
			try {

				taxonService.saveOne(taxon);
			} catch (Exception e) {
				logger.info("error 00001 ,错误信息如下" + e.getMessage() + "，路径：" + path);
				for (PlantEncyclopediaExcelVO row : sheetValues) {
					try {
						toolService.printEntity(row);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
				e.printStackTrace();
			}
		}

	}

	/**
	 * 
	 * @Description B列为描述类型，D列为描述原文
	 * @param row
	 * @param taxon
	 * @param path
	 * @param params
	 * @author ZXY
	 */
	private void insertByColB(PlantEncyclopediaExcelVO row, Taxon taxon, String path, BaseParamsForm params) {
		// B列是描述类型，D列是描述原文
		String colB = row.getColB().trim();
		String colD = row.getColD();
		if (StringUtils.isEmpty(colB) || StringUtils.isEmpty(colD) || unColBDescTitle.contains(colB)) {
			return;
		}
		String SourceColB = colB;// 原标题
		Descriptiontype descriptiontype = descriptiontypeService.findOneByName(colB);
		if (descriptiontype == null) {
			colB = toDBEescType(colB);
			descriptiontype = descriptiontypeService.findOneByName(colB);
			if (descriptiontype == null) {
				logger.info("error DT00001 ,在【描述类型表】中查询不到此描述类型："+colB+"");
				return;
			}
		}
		if (params.isInsert()) {
			// save
			descriptionService.insertDescription(descriptiontype, SourceColB + "：" + colD, taxon, params);
		}

	}

	private String toDBEescType(String colB) {
		if (colB.contains("生境")) {
			colB = "生境信息";
		} else if (colB.contains("标本")) {
			colB = "标本信息";
		} else if (colB.contains("遗传")) {
			colB = "遗传学";
		} else if (colB.contains("分布")) {
			colB = "分布信息";
		} else if (colB.contains("海拔")) {
			colB = "海拔范围";
		} else if (colB.contains("地理区")) {
			colB = "地理区分布";
		} else if (colB.contains("亲缘")) {
			colB = "亲缘关系";
		} else if (colB.contains("用途")) {
			colB = "经济用途";
		} else if (colB.contains("经济意义")) {
			colB = "经济意义";
		} else if (colB.contains("世界物种数")) {
			colB = "全球物种数估计";
		} else if (colB.contains("中国物种数")) {
			colB = "国内物种数估计";
		} else if (colB.contains("鉴别") || colB.contains("鉴定")) {
			colB = "鉴别特征";
		}

		return colB;
	}

	private void insertByColA(PlantEncyclopediaExcelVO row, Taxon taxon, String path, BaseParamsForm params) {

		String colA = row.getColA();
		String colD = row.getColD();
		// colA
		if (colA.contains("物种学名")) {
			handleSciNameRow(row, taxon, path, params);
		} else if (colA.contains("物种中文名")) {
			if (CommUtils.isStrNotEmpty(colD)) {
				taxon.setChname(colD.trim());
			} else {
				// 根据文件名称获取
				String[] split = StringUtils.split(path, "\\");
				String name = CommUtils.cutChinese(split[split.length - 1]);
				taxon.setChname(name.trim());
			}
		} else if (colA.contains("引证信息") || colA.contains("分类概念依据")) {
//			List<Citation> citationList = citationService.findCitationListByTaxonId(taxon.getId());
			if (CommUtils.isStrNotEmpty(colD)) {
				// rank = var 且 Scientificname不包含var. 且 colD以var.开头
				if (String.valueOf(RankEnum.var.getIndex()).equals(taxon.getRankid())
						&& StringUtils.isNotEmpty(taxon.getScientificname())
						&& !taxon.getScientificname().contains("var.") && colD.startsWith("var.")) {
					String substringBefore = StringUtils.substringBefore(colD, " in ");
					if (StringUtils.isNotEmpty(substringBefore)) {
						JSONObject object = varNameParser(
								taxon.getScientificname().trim() + " " + substringBefore.trim(), "var.", path);
						taxon.setEpithet(String.valueOf(object.get("Epithet")));
						taxon.setScientificname(String.valueOf(object.get("CanonicalName")));
						taxon.setAuthorstr(String.valueOf(object.get("Authorstr")));
						
					}
				}
				Citation citation = new Citation();
				EntityInit.initCitation(citation, params);
				citation.setSciname(taxon.getScientificname());
				citation.setAuthorship(taxon.getAuthorstr());
				citation.setTaxon(taxon);
				citation.setNametype(NametypeEnum.acceptedName.getIndex());
				citation.setCitationstr(colD);
				citation.setRefjson(turnTaxonRefToCitation(taxon.getRefjson()));
				citation.setExpert(params.getmExpert());
				citation.setSourcesid(taxon.getSourcesid());
				citation.setSourcesidId(taxon.getSourcesid());
				System.out.println(taxon.getSourcesid());
				if (params.isInsert()) {
					citationService.save(citation);
				}
			} else if (CommUtils.isStrNotEmpty(colD) && colD.contains("var.")) {
				taxon.setRankid(RankEnum.var.getIndex());
			} else if (CommUtils.isStrNotEmpty(colD) && colD.contains("subsp.")) {
				taxon.setRankid(RankEnum.subsp.getIndex());
			}

		} else if (colA.contains("俗名信息")) {
			if (CommUtils.isStrNotEmpty(colD)) {
				colD = toolService.replaceAllChar(colD, "[,，]", "、");
				colD = colD.replace("）", "）、");
				colD = colD.replace(")", ")、");
				String[] commNames = colD.split("、");
				for (String oneName : commNames) {
					if(StringUtils.isEmpty(oneName)) {
						continue;
					}
					Commonname commonname = new Commonname();
					commonname.setCommonname(oneName);
					EntityInit.initCommonname(commonname, params);
					commonname.setTaxon(taxon);
					commonname.setExpert(params.getmExpert());
					commonname.setLanguage(String.valueOf(LanguageEnum.chinese.getIndex()));
					commonname.setRefjson(taxon.getRefjson());
					commonname.setSourcesid(taxon.getSourcesid());
					if (params.isInsert()) {
						commonnameService.saveOne(commonname);
					}
				}
			}
		} else if (colA.contains("文献信息")) {
			if (StringUtils.isBlank(taxon.getSourcesid())) {
				// 参考文献和数据源
				handleRefAndDataSource(row, taxon, path, params);
			}
		} else if (colA.contains("专家信息")) {
			if (StringUtils.isNotBlank(colD)) {
				// 参考文献和数据源
				taxon.setRemark(turnJsonRemark(JsonExpertName, colD, taxon.getRemark()));
			}
		} else if ((colA.contains("经济意义") || colA.contains("遗传信息")) || colA.contains("物种数") || colA.contains("亲缘")
				|| colA.contains("用途") || colA.contains("标本") || colA.contains("花果期")) {
			// 保存到描述表
			if (StringUtils.isNotBlank(colD)) {
				row.setColB(colA);
				insertByColB(row, taxon, path, params);
			}
		} else {
			if (CommUtils.isStrNotEmpty(colA) && !unColADescTitle.contains(colA.trim())) {
				System.out.println("error B00001  未知：colA: " + colA + ", " + path);
			}
		}

	}

	private void handleRefAndDataSource(PlantEncyclopediaExcelVO row, Taxon taxon, String path, BaseParamsForm params) {
		String col = null;
		String colE = row.getColE();
		String colD = row.getColD();
		if (CommUtils.isStrNotEmpty(colE)) {
			col = colE;
		} else if (CommUtils.isStrNotEmpty(colD)) {
			col = colD;
		}
		// colE 数据源/参考文献
		if (CommUtils.isStrNotEmpty(col)) {
			// 数据源
			Datasource datasource = datasourceService.insertDSIfNotExist(col, params.getmLoginUser(),
					params.getmLoginUser());
			taxon.setSourcesid(datasource.getId());

			// 参考文献
			Ref ref = refService.insertRefIfNotExist(col, params.getmLoginUser(), refRemark);
			taxon.setRefjson(turnRefToJson(ref));
			taxon.setRemark(turnJsonRemark(ClassificationConcept, col, taxon.getRemark()));
		}

	}

	private String turnTaxonRefToCitation(String refjson) {
		if (CommUtils.isStrEmpty(refjson)) {
			return null;
		}
		JSONArray array = JSONArray.parseArray(refjson);
		for (int i = 0; i < array.size(); i++) {
			JSONObject jsonObject = array.getJSONObject(i); // 遍历 jsonarray 数组，把每一个对象转成 json 对象
			jsonObject.put("refType", String.valueOf(RefTypeEnum.other.getIndex()));
		}
		return array.toString();
	}

	private void handleSciNameRow(PlantEncyclopediaExcelVO row, Taxon taxon, String path, BaseParamsForm params) {
		String colD = row.getColD();// 学名和命名人,有可能为空
		if (CommUtils.isStrNotEmpty(colD)) {
			colD = colD.replace(" ", " ");
			colD = colD.trim();
		}
		String colE = row.getColE();// 来源
		String colF = row.getColF();// 审核专家，可能为空
		// 根据路径确定分类等级
		int rankId = judgeRankIsWhatByPath(path).getIndex();
		taxon.setRankid(rankId);
		// colD 拉丁名和命名信息 按照空格拆分
		if (CommUtils.isStartWithEnglish(colD)) {
			// remove 去除注后面的内容
			if (colD.contains("注：")) {
				colD = CommUtils.cutByStrBefore(colD, "注：");
			}
			// countTargetStr 计算空格个数
			int spaceCount = toolService.countTargetStr(colD, " ");
			if (spaceCount >= 2) {
				String sciName = null;
				if (colD.contains("var.") || colD.contains("subsp.")) {
					String flag = "";
					if (colD.contains("var.")) {
						flag = "var.";
						taxon.setRankid(RankEnum.var.getIndex());
					} else {
						flag = "subsp.";
						taxon.setRankid(RankEnum.subsp.getIndex());
					}
					try {
						JSONObject object = varNameParser(colD, flag, path);
						taxon.setEpithet(String.valueOf(object.get("Epithet")));
						taxon.setScientificname(String.valueOf(object.get("CanonicalName")));
						taxon.setAuthorstr(String.valueOf(object.get("Authorstr")));
					} catch (Exception e) {
						System.out.println(" K00001 colD:" + colD + "||||" + e.getMessage());
						e.printStackTrace();
					}
				} else if (rankId != RankEnum.species.getIndex()) {
					String[] splitD = StringUtils.split(colD, " ");
					if (rankId == RankEnum.var.getIndex() && splitD.length == 3) {
						taxon.setEpithet(String.valueOf(splitD[2]));// 种加词
						taxon.setScientificname(colD);// 学名
					} else {
						String response = HttpUtils.doGet(url, "name=" + colD);
						JSONObject object = CommUtils.strToJSONObject(response);
						sciName = String.valueOf(object.get("CanonicalName"));
						taxon.setEpithet(String.valueOf(object.get("Epithet")));// 种加词
						taxon.setAuthorstr(String.valueOf(object.get("Author")));// 命名人
						taxon.setScientificname(sciName.trim());// 学名
					}

				} else {
					sciName = colD.substring(0, colD.indexOf(" ", colD.indexOf(" ") + 1));
					taxon.setAuthorstr(CommUtils.cutByStrAfter(colD, sciName).trim());// 命名人
					taxon.setEpithet(CommUtils.cutByStrAfter(sciName, " "));// 种加词
					taxon.setScientificname(sciName.trim());// 学名
				}

			} else if (spaceCount == 1) {
				taxon.setScientificname(colD);
				taxon.setEpithet(CommUtils.cutByStrAfter(colD, " "));// 种加词
			} else {
				logger.info("error , 无法根据空格截取sciname,colD =" + colD + ",path=" + path);
			}
		} else if (CommUtils.isStrNotEmpty(colD)) {
			logger.info("error , E00001, D列不是物种学名...,colD =" + colD + ", path =" + path);
		}
		// colE 数据源/参考文献
		if (CommUtils.isStrNotEmpty(colE)) {
			handleRefAndDataSource(row, taxon, path, params);
		}
		// remark
		if (CommUtils.isStrNotEmpty(colF)) {
			taxon.setRemark(turnJsonRemark(JsonExpertName, colF, taxon.getRemark()));
			// 此审核专家放入remark中
		}
		// 统一使用前台传过来的审核专家
		taxon.setExpert(params.getmExpert());
		// 路径
		taxon.setRemark(
				turnJsonRemark(relativeExcelPath, CommUtils.cutByStrAfter(path, "汇交专项-植物专题"), taxon.getRemark()));
	}

	private JSONObject varNameParser(String colD, String flag, String path) {
		JSONObject obj = new JSONObject();
		String sciName = "";
		String epithet = "";
		try {

			String[] varsplit = StringUtils.split(colD, " ");
			sciName = varsplit[0] + " " + varsplit[1];

			for (int j = 0; j < varsplit.length; j++) {
				String str = varsplit[j];
				if (str.contains(flag)) {
					sciName = sciName + " " + flag + " " + varsplit[j + 1];// 学名
					epithet = varsplit[j + 1];// 种加词
					break;
				}
			}
			// 作者
			String[] nameSplit = sciName.split(" ");
			for (String str : nameSplit) {
				colD = StringUtils.remove(colD, str);
			}
		} catch (Exception e) {
			logger.info("K00001 解析" + flag + "出错，" + path + "," + colD);
			e.printStackTrace();
		}
		obj.put("CanonicalName", sciName.trim());
		obj.put("Epithet", epithet.trim());
		obj.put("Authorstr", colD.replace("  ", " "));
		return obj;
	}

	private String turnJsonRemark(String name, String value, String oldRemark) {
		JSONObject jsonObject = null;
		if (CommUtils.isStrEmpty(oldRemark)) {
			jsonObject = new JSONObject();
		} else {
			jsonObject = CommUtils.strToJSONObject(oldRemark);
		}
		jsonObject.put(name, value);
		return String.valueOf(jsonObject);
	}

	private String turnRefToJson(Ref ref) {
		JSONArray jsonArray = new JSONArray();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("refE", " 0");
		jsonObject.put("refS", " 0");
		jsonObject.put("refId", ref.getId());
		jsonArray.add(jsonObject);
		return jsonArray.toJSONString();
	}

	/**
	 * 根据路径判断是什么分类等级
	 * 
	 * @Description
	 * @param path
	 * @return
	 * @author ZXY
	 */

	public RankEnum judgeRankIsWhatByPath(String path) {
		String[] splitPath = StringUtils.split(path, "\\");
		String excelName = splitPath[splitPath.length - 1].replace("．", ".");// 1.喜马拉雅崖爬藤.xlsx
		Pattern pattern = Pattern.compile("\\d{1,}[a-zA-Z]{1,}");
		Matcher matcher = pattern.matcher(excelName);
		if (excelName.contains("变种") && !excelName.contains("含") && !excelName.contains("原变种")) {
			// 变种
			return RankEnum.var;
		} else if (excelName.contains("变种") && excelName.contains("含")) {
			// 种
			return RankEnum.species;
		} else if (excelName.contains("原变种")) {
			// 种
			return RankEnum.species;
		} else if (excelName.contains("变型")) {
			// 变型
			return RankEnum.Forma;
		} else if (excelName.contains("亚种")) {
			// 亚种
			return RankEnum.subsp;
		} else if (matcher.find()) {
			// 变种
			return RankEnum.var;
		} else {
			String upperRank = splitPath[splitPath.length - 2].replace("．", ".").replace("(", "（");// 31.喜马拉雅崖爬藤（含一个变种）
			String onlyChUpperRank = StringUtils.substringBefore(CommUtils.cutChinese(upperRank), "含");// 喜马拉雅崖爬藤
			String chineseName = CommUtils.cutByStrAfter(CommUtils.cutByStrBefore(excelName, ".x"), ".").trim();// 喜马拉雅崖爬藤
			chineseName = CommUtils.cutChinese(chineseName);
			if (upperRank.contains("组") || upperRank.contains("属") || upperRank.contains("系")
					|| upperRank.contains("存疑种")) {
				// 种
				return RankEnum.species;
			} else if (onlyChUpperRank.equals(chineseName)) {
				// 种
				return RankEnum.species;
			} else if (chineseName.contains(onlyChUpperRank)) {
				// 变种
				return RankEnum.var;
			} else {
				// 其余都视为变种
				return RankEnum.var;
			}
		}

	}
}
