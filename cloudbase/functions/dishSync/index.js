const { dishSyncInternal } = require('./_shared/dishHandlers');
exports.main = async function main(event) {
  return dishSyncInternal(event || {});
};
