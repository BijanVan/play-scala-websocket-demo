import * as d3 from 'd3';
import bar from 'britecharts/dist/umd/bar.min';
import * as britecharts from "britecharts";

import 'britecharts/dist/css/britecharts.min.css'

const data = [
  {name: 'Shiny', id: 1, quantity: 86, percentage: 5},
  {name: 'Blazing', id: 2, quantity: 300, percentage: 18},
  {name: 'Dazzling', id: 3, quantity: 276, percentage: 16},
  {name: 'Radiant', id: 4, quantity: 195, percentage: 11},
  {name: 'Sparkling', id: 5, quantity: 36, percentage: 2},
  {name: 'Other', id: 0, quantity: 814, percentage: 48}
];

function createHorizontalBarChart(tag) {
  const barChart1 = bar();
  const margin = {
    left: 120,
    right: 20,
    top: 20,
    bottom: 30
  };
  const barContainer = d3.select(tag);
  const containerWidth = barContainer.node() ? barContainer.node().getBoundingClientRect().width : false;

  barChart1
    .horizontal(true)
    .margin(margin)
    .width(containerWidth)
    .colorSchema(britecharts.colors.colorSchemas.britechartsColorSchema)
    .valueLabel('percentage')
    .height(300);

  barContainer.datum(data).call(barChart1);
}

createHorizontalBarChart('.js-bar-container');
