var exec = require('cordova/exec');

var printer = {
   find: function(fnSuccess, fnError){
      exec(fnSuccess, fnError, "BluetoothPrinter", "find", []);
   },
   open: function(fnSuccess, fnError){
      exec(fnSuccess, fnError, "BluetoothPrinter", "open", []);
   },
   close: function(fnSuccess, fnError){
      exec(fnSuccess, fnError, "BluetoothPrinter", "close", []);
   },
   print: function(fnSuccess, fnError, str){
      exec(fnSuccess, fnError, "BluetoothPrinter", "print", [str]);
   }
};

module.exports = printer;
