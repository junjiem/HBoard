'use strict';
cBoard.service('chartGridService', function () {

    this.render = function (containerDom, option, scope, persist) {
        if (option == null) {
            containerDom.html("<div class=\"alert alert-danger\" role=\"alert\">No Data!</div>");
            return;
        }
        var height;
        scope ? height = scope.myheight - 20 : null;
        return new CBoardGridRender(containerDom, option).do(height, persist);
    };

    this.parseOption = function (data) {
        var gridOption = gridDataProcess(data.chartConfig, data.keys, data.series, data.data, data.seriesConfig);
        return gridOption;
    };
});

function gridDataProcess(chartConfig, casted_keys, casted_values, aggregate_data, newValuesConfig) {
    var columnDefs = [],
        rowData = [];
    var newValue = changeJsonToArr(newValuesConfig);
    for (var i = 0; i < chartConfig.keys.length; i++) {
        columnDefs.push({
            headerName: chartConfig.keys[i].col,
            field: chartConfig.keys[i].col,
            enableRowGroup: true,
            enableValue: true,
            rowGroup: true
        })
    }
    //如果含有列维
    if (chartConfig.groups.length > 0) {
        var map = [];
        var arr = [];
        for (var i = 0; i < casted_values[0].length; i++) {
            map.push({});
            for (var j = 0; j < casted_values.length; j++) {
                if (!map[i][casted_values[j][i]]) {
                    map[i][casted_values[j][i]] = casted_values[j][i];
                }
            }
        }
        var getChildren = function (name,index) {
            index ++;
            var new_arr = [];
            if (index == map.length - 1) {
                for (var j in map[index]) {
                    var field = name + "-" + j;
                    new_arr.push({
                        headerName: j,
                        field: field,
                        enableValue:true,
                        enableRowGroup:true,
                    })
                }
            } else {
                for (var j in map[index]) {
                    var field = name + "-" + j;
                    new_arr.push({
                        headerName: j,
                        groupId:j,
                        children:getChildren(field,index)
                    })
                }
            }
            return new_arr;
        }
        for (var i in map[0]) {
            var index = 0;
            var name = i;
            arr.push({
                headerName: i,
                groupId: i,
                children: getChildren(name,index)
            })
        }
        for (var i = 0; i < arr.length; i++) {
            columnDefs.push(arr[i]);
        }
        console.log(arr);
        console.log(columnDefs);
    } else {
        for (var i = 0; i < casted_values.length; i++) {
            columnDefs.push({
                headerName: casted_values[i][0],
                field: casted_values[i][0],
                enableRowGroup: true,
                enableValue: true
            })
        }
    }

    for (var i = 0; i < aggregate_data[0].length; i++) {
        var rowItem = {};
        for (var z = 0; z < chartConfig.keys.length; z++) {
            if(checkNumber(casted_keys[i][z])){
                rowItem[chartConfig.keys[z].col] = parseInt(casted_keys[i][z]);
            }else{
                rowItem[chartConfig.keys[z].col] = casted_keys[i][z];
            }
        }
        for (var j = 0; j < newValue.length; j++) {
            if(checkNumber(aggregate_data[j][i])){
                rowItem[newValue[j]] = parseInt(aggregate_data[j][i]);
            }else{
                rowItem[newValue[j]] = aggregate_data[j][i];
            }
        }
        rowData.push(rowItem);
    }
    console.log(rowData);

    var gridOption = {
        columnDefs: columnDefs,
        rowData: rowData,
        enableFilter: true,
        enableSorting: true,
        animateRows: true,
        floatingFilter: true
    }
    return gridOption;
}

function getJsonLength(jsonData) {
    var length = 0;
    for (var l in jsonData) {
        length++;
    }
    return length;
}

function changeJsonToArr(jsonData) {
    var arr = [];
    for (var l in jsonData) {
        arr.push(l);
    }
    return arr;
}
function checkNumber(theObj) {
    var reg = /^[0-9]+.?[0-9]*$/;
    if (reg.test(theObj)) {
        return true;
    }
    return false;
}
